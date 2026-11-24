package br.com.erp.financeiro.cobrancas;

import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.BaixaManualEntrada;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.CobrancaEntrada;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.CobrancaSaida;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.pagamentos.Pagamento;
import br.com.erp.financeiro.pagamentos.PagamentoSaida;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-01 (contas a receber) e RF-12 (baixa manual). */
@Tag(name = "contas-a-receber")
@RestController
@RequestMapping("/api/financeiro/cobrancas")
public class CobrancaController {

  private static final Map<String, Sort> ORDENS = Map.of(
      "vencimento,asc", Sort.by("dataVencimento").ascending().and(Sort.by("id")),
      "vencimento,desc", Sort.by("dataVencimento").descending().and(Sort.by("id")),
      "criadoEm,desc", Sort.by("createdAt").descending().and(Sort.by("id")));

  private final CobrancaService cobrancas;
  private final Relogio relogio;

  public CobrancaController(CobrancaService cobrancas, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.relogio = relogio;
  }

  static Sort ordem(String ordenar) {
    if (ordenar == null || ordenar.isBlank()) return ORDENS.get("vencimento,asc");
    Sort s = ORDENS.get(ordenar);
    if (s == null) throw br.com.erp.financeiro.api.ApiException.campo("ordenar", "ORDENACAO_INVALIDA",
        "Use vencimento,asc, vencimento,desc ou criadoEm,desc.");
    return s;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<Pagina<CobrancaSaida>> listar(
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamanho,
      @RequestParam(required = false) UUID clienteId,
      @RequestParam(required = false) StatusCobranca status,
      @RequestParam(required = false) LocalDate vencimentoInicio,
      @RequestParam(required = false) LocalDate vencimentoFim,
      @RequestParam(required = false) UUID recorrenciaId,
      @RequestParam(required = false) String ordenar) {
    return Resposta.ok(cobrancas.listar(
        new CobrancaService.Filtro(clienteId, status, vencimentoInicio, vencimentoFim, recorrenciaId),
        Pagina.pedido(pagina, tamanho, ordem(ordenar))));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('financeiro.contareceber.criar')")
  public ResponseEntity<Resposta<CobrancaSaida>> criar(@Valid @RequestBody CobrancaEntrada e) {
    Cobranca c = cobrancas.lancar(e);
    return ResponseEntity.created(URI.create("/api/financeiro/cobrancas/" + c.getId()))
        .body(Resposta.ok(CobrancaSaida.de(c, relogio.hoje()), "Cobrança lançada."));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<CobrancaSaida> ver(@PathVariable UUID id) {
    return Resposta.ok(CobrancaSaida.de(cobrancas.obter(id), relogio.hoje()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public Resposta<CobrancaSaida> editar(@PathVariable UUID id, @Valid @RequestBody CobrancaEntrada e) {
    return Resposta.ok(CobrancaSaida.de(cobrancas.editar(id, e), relogio.hoje()), "Cobrança atualizada.");
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.contareceber.excluir')")
  public Resposta<Void> cancelar(@PathVariable UUID id) {
    cobrancas.cancelar(id);
    return Resposta.ok(null, "Cobrança cancelada.");
  }

  /** RF-12: lacuna no contrato v0.3, proposta em contratos/financeiro.yaml v0.4. */
  @PostMapping("/{id}/baixas")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public ResponseEntity<Resposta<PagamentoSaida>> baixar(@PathVariable UUID id, @Valid @RequestBody BaixaManualEntrada e) {
    Pagamento p = cobrancas.baixaManual(id, e);
    return ResponseEntity.created(URI.create("/api/financeiro/pagamentos/" + p.getId()))
        .body(Resposta.ok(PagamentoSaida.de(p), "Baixa registrada."));
  }
}
