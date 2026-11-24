package br.com.erp.financeiro.cobrancas;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.BaixaManualEntrada;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.CobrancaEntrada;
import br.com.erp.financeiro.comum.Competencia;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.comum.Tenant;
import br.com.erp.financeiro.eventos.Outbox;
import br.com.erp.financeiro.eventos.PedidosPlataforma;
import br.com.erp.financeiro.eventos.TiposDeEvento;
import br.com.erp.financeiro.inadimplencia.PromessaPagamento;
import br.com.erp.financeiro.inadimplencia.PromessaRepository;
import br.com.erp.financeiro.pagamentos.Pagamento;
import br.com.erp.financeiro.pagamentos.PagamentoRepository;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a receber (RF-01), baixa manual (RF-12) e o efeito comum de todo pagamento confirmado (RN-24). */
@Service
public class CobrancaService {

  public static final String ENTIDADE = "cobranca";
  private static final String ROTA = "/modulos/financeiro/cobrancas/";

  private final CobrancaRepository cobrancas;
  private final PagamentoRepository pagamentos;
  private final PromessaRepository promessas;
  private final AuditoriaService auditoria;
  private final Outbox outbox;
  private final PedidosPlataforma plataforma;
  private final ProvedorPagamento provedor;
  private final Relogio relogio;

  public CobrancaService(CobrancaRepository cobrancas, PagamentoRepository pagamentos, PromessaRepository promessas,
      AuditoriaService auditoria, Outbox outbox, PedidosPlataforma plataforma, ProvedorPagamento provedor, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.pagamentos = pagamentos;
    this.promessas = promessas;
    this.auditoria = auditoria;
    this.outbox = outbox;
    this.plataforma = plataforma;
    this.provedor = provedor;
    this.relogio = relogio;
  }

  public record Filtro(UUID clienteId, StatusCobranca status, LocalDate vencimentoInicio, LocalDate vencimentoFim,
      UUID recorrenciaId) {}

  @Transactional(readOnly = true)
  public Pagina<CobrancaDtos.CobrancaSaida> listar(Filtro f, Pageable pagina) {
    LocalDate hoje = relogio.hoje();
    return Pagina.de(cobrancas.findAll(especificacao(f), pagina), c -> CobrancaDtos.CobrancaSaida.de(c, hoje));
  }

  /** Canceladas saem da listagem padrão (RN-04) e voltam quando o filtro pede status=CANCELADO. */
  public static Specification<Cobranca> especificacao(Filtro f) {
    Specification<Cobranca> s = (r, q, cb) -> f.status() == StatusCobranca.CANCELADO
        ? cb.equal(r.get("status"), StatusCobranca.CANCELADO)
        : cb.isNull(r.get("deletedAt"));
    if (f.status() != null && f.status() != StatusCobranca.CANCELADO) s = s.and((r, q, cb) -> cb.equal(r.get("status"), f.status()));
    if (f.clienteId() != null) s = s.and((r, q, cb) -> cb.equal(r.get("empresaId"), f.clienteId()));
    if (f.recorrenciaId() != null) s = s.and((r, q, cb) -> cb.equal(r.get("recorrenciaId"), f.recorrenciaId()));
    if (f.vencimentoInicio() != null) s = s.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("dataVencimento"), f.vencimentoInicio()));
    if (f.vencimentoFim() != null) s = s.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("dataVencimento"), f.vencimentoFim()));
    return s;
  }

  @Transactional(readOnly = true)
  public Cobranca obter(UUID id) {
    return Tenant.doTenant(cobrancas.findById(id), "Cobrança");
  }

  @Transactional
  public Cobranca lancar(CobrancaEntrada e) {
    validarDesconto(e.valor(), e.desconto());
    Cobranca c = Cobranca.nova(e.clienteId(), e.contratoId(), null, Competencia.de(e.competencia()), e.descricao(),
        e.vencimento(), e.valor(), e.desconto(), e.juros(), e.multa(), e.formaPagamento());
    if (e.vencimento().isBefore(relogio.hoje())) c.marcarAtrasada();
    return registrarNova(c);
  }

  /** Grava, audita e avisa (financeiro.cobranca.criada depois do commit). Usado também pela recorrência. */
  @Transactional
  public Cobranca registrarNova(Cobranca c) {
    cobrancas.save(c);
    auditoria.registrar("CRIAR", ENTIDADE, c.getId(), null, c.instantaneo());
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("cobrancaId", c.getId());
    dados.put("clienteId", c.getEmpresaId());
    dados.put("empresaId", c.getEmpresaId());
    dados.put("contratoId", c.getContratoId());
    dados.put("valor", c.totalDevido());
    dados.put("vencimento", c.getDataVencimento());
    outbox.gravar(TiposDeEvento.COBRANCA_CRIADA, dados);
    return c;
  }

  /** RN-05: valor só em ABERTO/AGENDADO; com pagamento parcial, o valor original fica travado. */
  @Transactional
  public Cobranca editar(UUID id, CobrancaEntrada e) {
    Cobranca c = obterAtiva(id);
    Map<String, Object> antes = c.instantaneo();
    if (c.getStatus() == StatusCobranca.PARCIAL) {
      if (e.valor().compareTo(c.getValor()) != 0 || !e.clienteId().equals(c.getEmpresaId())) {
        throw ApiException.conflito("EDICAO_NAO_PERMITIDA",
            "Cobrança com pagamento parcial: o valor original e a empresa não mudam; só o saldo em aberto é editável.");
      }
    } else if (!c.podeEditarValor()) {
      throw ApiException.conflito("EDICAO_NAO_PERMITIDA", "Cobrança " + c.getStatus() + " não pode ser editada.");
    }
    validarDesconto(e.valor(), e.desconto());
    BigDecimal devido = e.valor().subtract(Dinheiro.ouZero(e.desconto())).add(Dinheiro.ouZero(e.juros())).add(Dinheiro.ouZero(e.multa()));
    if (devido.compareTo(c.getValorPago()) <= 0) {
      throw ApiException.regra("SALDO_INVALIDO", "A edição deixaria a cobrança sem saldo; use a baixa para quitá-la.");
    }
    c.editar(e.clienteId(), e.contratoId(), Competencia.de(e.competencia()), e.descricao(), e.vencimento(), e.valor(),
        e.desconto(), e.juros(), e.multa(), e.formaPagamento());
    c.reavaliarAtraso(relogio.hoje());
    auditoria.registrar("EDITAR", ENTIDADE, c.getId(), antes, c.instantaneo());
    return c;
  }

  /** RN-04: cancelar é exclusão lógica. Só sem dinheiro recebido; pedidos pendentes no provedor caem. */
  @Transactional
  public void cancelar(UUID id) {
    Cobranca c = obterAtiva(id);
    if (!(c.getStatus() == StatusCobranca.ABERTO || c.getStatus() == StatusCobranca.AGENDADO
        || c.getStatus() == StatusCobranca.ATRASADO) || c.getValorPago().signum() > 0) {
      throw ApiException.conflito("CANCELAMENTO_NAO_PERMITIDO",
          "Só cobrança sem pagamento recebido (ABERTO, AGENDADO ou ATRASADO) pode ser cancelada.");
    }
    Map<String, Object> antes = c.instantaneo();
    for (Pagamento p : pagamentos.daCobranca(c.getId())) {
      if (p.pendente()) {
        if (p.getReferenciaProvedor() != null) provedor.cancelPayment(p.getReferenciaProvedor());
        p.expirar();
      }
    }
    c.cancelar();
    auditoria.registrar("CANCELAR", ENTIDADE, c.getId(), antes, c.instantaneo());
  }

  /** RF-12 / RN-60: gera pagamento TRANSFERENCIA já confirmado, com os mesmos efeitos do webhook. */
  @Transactional
  public Pagamento baixaManual(UUID id, BaixaManualEntrada e) {
    Cobranca c = obterAtiva(id);
    if (!StatusCobranca.PAGAVEIS.contains(c.getStatus())) {
      throw ApiException.conflito("COBRANCA_NAO_PAGAVEL", "Cobrança " + c.getStatus() + " não aceita baixa.");
    }
    if (e.valor().compareTo(c.saldo()) > 0) {
      throw ApiException.regra("VALOR_ACIMA_DO_SALDO", "O valor da baixa (" + Dinheiro.brl(e.valor())
          + ") é maior que o saldo em aberto (" + Dinheiro.brl(c.saldo()) + ").");
    }
    LocalDate data = e.dataPagamento() != null ? e.dataPagamento() : relogio.hoje();
    if (data.isAfter(relogio.hoje())) throw ApiException.campo("dataPagamento", "DATA_FUTURA", "A data do pagamento não pode ser futura.");
    OffsetDateTime quando = data.equals(relogio.hoje()) ? relogio.agoraUtc()
        : data.atTime(LocalTime.NOON).atZone(Relogio.BRASILIA).toOffsetDateTime();
    Pagamento p = pagamentos.save(Pagamento.manual(c.getId(), e.valor(), quando, e.comprovanteId()));
    auditoria.registrar("BAIXA_MANUAL", "pagamento", p.getId(), null, p.instantaneo());
    aplicarPagamentoConfirmado(c, p);
    return p;
  }

  /**
   * Efeito comum de todo pagamento confirmado — webhook ou baixa manual (RN-24, RN-29, RN-35, RN-60):
   * abate o saldo, muda o status, cumpre promessas, gera histórico e avisa a plataforma e os módulos.
   * As etapas futuras da régua deixam de disparar porque o job só considera cobranças em aberto (RN-35).
   */
  @Transactional
  public void aplicarPagamentoConfirmado(Cobranca c, Pagamento p) {
    Map<String, Object> antes = c.instantaneo();
    StatusCobranca novo = c.aplicarPagamento(p.getValor());
    auditoria.registrar("BAIXAR", ENTIDADE, c.getId(), antes, c.instantaneo());
    for (PromessaPagamento promessa : promessas.pendentesDaCobranca(c.getId())) {
      if (novo == StatusCobranca.PAGO || p.getValor().compareTo(promessa.getValor()) >= 0) promessa.cumprir();
    }
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("pagamentoId", p.getId());
    dados.put("cobrancaId", c.getId());
    dados.put("clienteId", c.getEmpresaId());
    dados.put("empresaId", c.getEmpresaId());
    dados.put("valorPago", p.getValor());
    dados.put("meio", p.getMeio());
    dados.put("statusCobranca", novo);
    dados.put("saldo", c.saldo());
    outbox.gravar(TiposDeEvento.PAGAMENTO_CONFIRMADO, dados);
    plataforma.timeline(c.getEmpresaId(), TiposDeEvento.PAGAMENTO_CONFIRMADO,
        "Pagamento de " + Dinheiro.brl(p.getValor()) + " recebido (" + p.getMeio() + ") — cobrança "
            + curto(c.getId()) + (novo == StatusCobranca.PAGO ? " quitada." : ", saldo " + Dinheiro.brl(c.saldo()) + "."),
        ROTA + c.getId());
    if (c.getCreatedBy() != null) {
      plataforma.notificacao(c.getCreatedBy(), "COBRANCA_PAGA", "Pagamento recebido",
          "Cobrança " + curto(c.getId()) + ": " + Dinheiro.brl(p.getValor()) + " (" + novo + ").", ROTA + c.getId());
    }
  }

  /** Estorno ou falha informados pelo provedor: publica financeiro.pagamento.falhou (contrato: estorno sai como falhou). */
  @Transactional
  public void aplicarFalhaOuEstorno(Cobranca c, Pagamento p, boolean estorno, BigDecimal valorEstornado) {
    Map<String, Object> dados = new LinkedHashMap<>();
    dados.put("pagamentoId", p.getId());
    dados.put("cobrancaId", c.getId());
    dados.put("clienteId", c.getEmpresaId());
    dados.put("empresaId", c.getEmpresaId());
    dados.put("motivo", estorno ? "ESTORNADO" : "REJEITADO");
    if (estorno) {
      Map<String, Object> antes = c.instantaneo();
      c.estornar(valorEstornado, relogio.hoje());
      auditoria.registrar("ESTORNAR", ENTIDADE, c.getId(), antes, c.instantaneo());
      dados.put("valorPago", valorEstornado);
      plataforma.timeline(c.getEmpresaId(), TiposDeEvento.PAGAMENTO_FALHOU,
          "Pagamento de " + Dinheiro.brl(valorEstornado) + " estornado — cobrança " + curto(c.getId()) + ".", ROTA + c.getId());
    }
    dados.put("statusCobranca", c.getStatus());
    outbox.gravar(TiposDeEvento.PAGAMENTO_FALHOU, dados);
  }

  private Cobranca obterAtiva(UUID id) {
    Cobranca c = obter(id);
    if (c.getDeletedAt() != null) {
      throw ApiException.conflito("COBRANCA_CANCELADA", "Cobrança cancelada não aceita alteração.");
    }
    return c;
  }

  private static void validarDesconto(BigDecimal valor, BigDecimal desconto) {
    if (desconto != null && desconto.compareTo(valor) >= 0) {
      throw ApiException.campo("desconto", "DESCONTO_INVALIDO", "O desconto deve ser menor que o valor.");
    }
  }

  static String curto(UUID id) {
    return "#" + id.toString().substring(0, 8);
  }
}
