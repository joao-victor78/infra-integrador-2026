package br.com.erp.financeiro.pagamentos;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorSimulado;
import br.com.erp.financeiro.webhooks.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-04: PIX, boleto e link de cartão pelo adaptador; GET /pagamentos/{id} é o destino do Location. */
@Tag(name = "pagamentos")
@RestController
@Validated
@RequestMapping("/api/financeiro")
public class PagamentoController {

  private final PagamentoService pagamentos;
  private final ProvedorPagamento provedor;
  private final WebhookService webhooks;

  public PagamentoController(PagamentoService pagamentos, ProvedorPagamento provedor, WebhookService webhooks) {
    this.pagamentos = pagamentos;
    this.provedor = provedor;
    this.webhooks = webhooks;
  }

  @PostMapping("/cobrancas/{id}/pagamento/pix")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public ResponseEntity<Resposta<PagamentoSaida>> pix(@PathVariable UUID id) {
    return criado(pagamentos.solicitar(id, MeioPagamento.PIX));
  }

  @PostMapping("/cobrancas/{id}/pagamento/boleto")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public ResponseEntity<Resposta<PagamentoSaida>> boleto(@PathVariable UUID id) {
    return criado(pagamentos.solicitar(id, MeioPagamento.BOLETO));
  }

  @PostMapping("/cobrancas/{id}/pagamento/cartao")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public ResponseEntity<Resposta<PagamentoSaida>> cartao(@PathVariable UUID id) {
    return criado(pagamentos.solicitar(id, MeioPagamento.CARTAO));
  }

  @GetMapping("/pagamentos/{id}")
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<PagamentoSaida> ver(@PathVariable UUID id) {
    return Resposta.ok(PagamentoSaida.de(pagamentos.obter(id)));
  }

  /** Histórico de pagamentos da cobrança (tela de detalhe, RF-01). */
  @GetMapping("/cobrancas/{id}/pagamentos")
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<List<PagamentoSaida>> daCobranca(@PathVariable UUID id) {
    return Resposta.ok(pagamentos.daCobranca(id).stream().map(PagamentoSaida::de).toList());
  }

  public record SimulacaoEntrada(@Positive @Digits(integer = 13, fraction = 2) BigDecimal valor) {}

  /**
   * Só com o provedor simulado: faz o papel do provedor e envia o webhook assinado de confirmação,
   * para demonstrar a jornada até "pagamento recebido" sem conta de sandbox.
   */
  @Operation(summary = "Simular a confirmação do provedor (somente provedor simulado)")
  @PostMapping("/pagamentos/{id}/simular-confirmacao")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public Resposta<PagamentoSaida> simular(@PathVariable UUID id, @RequestBody(required = false) SimulacaoEntrada e) {
    if (!(provedor instanceof ProvedorSimulado simulado)) {
      throw ApiException.regra("PROVEDOR_REAL", "Simulação disponível só com o provedor simulado.");
    }
    Pagamento p = pagamentos.obter(id);
    if (!p.pendente()) throw ApiException.conflito("PAGAMENTO_NAO_PENDENTE", "O pagamento não está pendente.");
    BigDecimal valor = e != null && e.valor() != null ? e.valor() : p.getValor();
    String eventoId = "sim-" + UUID.randomUUID();
    String tipo = "pagamento.confirmado";
    webhooks.receber(new ProvedorPagamento.WebhookRecebido(eventoId, simulado.assinar(eventoId, tipo, p.getCobrancaId(), valor),
        tipo, p.getCobrancaId(), p.getId(), valor, null));
    return Resposta.ok(PagamentoSaida.de(pagamentos.obter(id)));
  }

  private static ResponseEntity<Resposta<PagamentoSaida>> criado(Pagamento p) {
    return ResponseEntity.created(URI.create("/api/financeiro/pagamentos/" + p.getId())).body(Resposta.ok(PagamentoSaida.de(p)));
  }
}
