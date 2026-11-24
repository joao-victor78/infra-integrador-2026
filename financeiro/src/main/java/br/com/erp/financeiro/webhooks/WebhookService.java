package br.com.erp.financeiro.webhooks;

import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.pagamentos.Pagamento;
import br.com.erp.financeiro.pagamentos.PagamentoRepository;
import br.com.erp.financeiro.pagamentos.StatusPagamento;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento.EventoProvedor;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento.TipoEventoProvedor;
import br.com.erp.financeiro.tenant.TenantContexto;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Baixa automática por webhook (RF-05). Sequência do §29 (RN-24): validar a assinatura, identificar a
 * cobrança, registrar o pagamento, baixar o título, atualizar a empresa, gerar histórico e cancelar os
 * avisos pendentes. O tenant é o da cobrança citada, nunca um parâmetro do chamador (RN-26).
 */
@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

  public enum Resultado { PROCESSADO, IGNORADO, REJEITADO, DUPLICADO, COBRANCA_DESCONHECIDA }

  private final ProvedorPagamento provedor;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final CobrancaRepository cobrancas;
  private final PagamentoRepository pagamentos;
  private final CobrancaService cobrancaService;
  private final AuditoriaService auditoria;
  private final Relogio relogio;

  public WebhookService(ProvedorPagamento provedor, JdbcTemplate jdbc, TransactionTemplate transacao,
      CobrancaRepository cobrancas, PagamentoRepository pagamentos, CobrancaService cobrancaService,
      AuditoriaService auditoria, Relogio relogio) {
    this.provedor = provedor;
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.cobrancas = cobrancas;
    this.pagamentos = pagamentos;
    this.cobrancaService = cobrancaService;
    this.auditoria = auditoria;
    this.relogio = relogio;
  }

  /** @throws ProvedorPagamento.AssinaturaInvalidaException antes de qualquer leitura ou escrita (RN-27) */
  public Resultado receber(ProvedorPagamento.WebhookRecebido recebido) {
    EventoProvedor evento = provedor.processWebhook(recebido);

    // Única leitura sem tenant: descobrir de quem é a cobrança citada (RN-26).
    List<UUID> tenants = jdbc.queryForList("SELECT tenant_id FROM financeiro.cobrancas WHERE id = ?", UUID.class,
        evento.cobrancaId());
    if (tenants.isEmpty()) {
      log.warn("Webhook {} cita cobrança inexistente; ignorado.", evento.eventoId());
      return Resultado.COBRANCA_DESCONHECIDA;
    }
    return TenantContexto.calcular(tenants.get(0), () -> transacao.execute(st -> processar(evento, recebido.tipo())));
  }

  private Resultado processar(EventoProvedor evento, String tipoOriginal) {
    UUID registro = UUID.randomUUID();
    Timestamp agora = Timestamp.from(relogio.agora());
    // RN-28: o id do evento do provedor é único; repetido responde 200 e é ignorado.
    int inseridos = jdbc.update("""
        INSERT INTO financeiro.webhook_eventos
          (id, tenant_id, provedor, evento_id_provedor, tipo, cobranca_id, assinatura_valida, resultado,
           recebido_em, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, true, 'PROCESSADO', ?, ?, ?)
        ON CONFLICT (provedor, evento_id_provedor) DO NOTHING""",
        registro, TenantContexto.exigir(), provedor.nome(), evento.eventoId(), tipoOriginal, evento.cobrancaId(),
        agora, agora, agora);
    if (inseridos == 0) {
      log.info("Webhook {} já processado; ignorado.", evento.eventoId());
      return Resultado.DUPLICADO;
    }

    Cobranca c = cobrancas.findById(evento.cobrancaId()).orElseThrow();
    Pagamento p = localizarPagamento(c, evento);
    Resultado resultado = switch (evento.tipo()) {
      case CONFIRMADO -> confirmar(c, p, evento);
      case FALHOU -> falhar(c, p);
      case ESTORNADO -> estornar(c, p, evento);
    };

    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("eventoId", evento.eventoId());
    resumo.put("tipo", tipoOriginal);
    resumo.put("valor", evento.valor());
    resumo.put("pagamentoId", p == null ? null : p.getId());
    resumo.put("resultado", resultado);
    auditoria.registrar("PROCESSAR_WEBHOOK", "webhook_evento", registro, null, resumo);
    pagamentos.flush(); // o pagamento novo precisa existir antes da FK de webhook_eventos
    jdbc.update("UPDATE financeiro.webhook_eventos SET pagamento_id = ?, resultado = ? WHERE id = ?",
        p == null ? null : p.getId(), resultado == Resultado.PROCESSADO ? "PROCESSADO" : "IGNORADO", registro);
    return resultado;
  }

  /** Pelo id informado; senão o pedido pendente mais recente da cobrança. */
  private Pagamento localizarPagamento(Cobranca c, EventoProvedor e) {
    if (e.pagamentoId() != null) {
      Optional<Pagamento> p = pagamentos.findById(e.pagamentoId()).filter(x -> x.getCobrancaId().equals(c.getId()));
      if (p.isPresent()) return p.get();
    }
    List<Pagamento> daCobranca = pagamentos.daCobranca(c.getId());
    StatusPagamento procurado = e.tipo() == TipoEventoProvedor.ESTORNADO ? StatusPagamento.CONFIRMADO : null;
    return daCobranca.stream()
        .filter(x -> procurado != null ? x.getStatus() == procurado : x.pendente())
        .findFirst().orElse(null);
  }

  private Resultado confirmar(Cobranca c, Pagamento p, EventoProvedor e) {
    if (c.getDeletedAt() != null || !StatusCobranca.PAGAVEIS.contains(c.getStatus())) {
      log.warn("Pagamento confirmado para cobrança {} em {}; registrado sem baixa.", c.getId(), c.getStatus());
      return Resultado.IGNORADO;
    }
    if (p != null && p.getStatus() == StatusPagamento.CONFIRMADO) return Resultado.IGNORADO;
    BigDecimal valor = e.valor() != null ? e.valor() : p != null ? p.getValor() : c.saldo();
    if (p == null) {
      MeioPagamento meio = c.getFormaPagamento() != null && c.getFormaPagamento() != MeioPagamento.TRANSFERENCIA
          ? c.getFormaPagamento() : MeioPagamento.PIX;
      p = Pagamento.pedido(c.getId(), meio, valor, provedor.nome());
    }
    p.confirmar(valor, e.quando());
    pagamentos.save(p);
    cobrancaService.aplicarPagamentoConfirmado(c, p);
    return Resultado.PROCESSADO;
  }

  private Resultado falhar(Cobranca c, Pagamento p) {
    if (p == null || !p.pendente()) return Resultado.IGNORADO;
    p.falhar();
    cobrancaService.aplicarFalhaOuEstorno(c, p, false, null);
    return Resultado.PROCESSADO;
  }

  private Resultado estornar(Cobranca c, Pagamento p, EventoProvedor e) {
    if (p == null || p.getStatus() != StatusPagamento.CONFIRMADO) return Resultado.IGNORADO;
    BigDecimal valor = e.valor() != null && e.valor().compareTo(p.getValor()) <= 0 ? e.valor() : p.getValor();
    p.estornar();
    cobrancaService.aplicarFalhaOuEstorno(c, p, true, valor);
    return Resultado.PROCESSADO;
  }
}
