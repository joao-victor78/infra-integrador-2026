package br.com.erp.financeiro.contaspagar;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.ContaPagarEntrada;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.ContaPagarSaida;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.PagamentoContaEntrada;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "contas-a-pagar")
@RestController
@RequestMapping("/api/financeiro/contas-pagar")
public class ContaPagarController {

  private static final Map<String, Sort> ORDENS = Map.of(
      "vencimento,asc", Sort.by("dataVencimento").ascending().and(Sort.by("id")),
      "vencimento,desc", Sort.by("dataVencimento").descending().and(Sort.by("id")),
      "criadoEm,desc", Sort.by("createdAt").descending().and(Sort.by("id")));

  private final ContaPagarService contas;

  public ContaPagarController(ContaPagarService contas) {
    this.contas = contas;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('financeiro.contapagar.ver')")
  public Resposta<Pagina<ContaPagarSaida>> listar(
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamanho,
      @RequestParam(required = false) Boolean aprovado,
      @RequestParam(required = false) ContaPagar.Status status,
      @RequestParam(required = false) String ordenar) {
    Sort ordem = ordenar == null || ordenar.isBlank() ? ORDENS.get("vencimento,asc") : ORDENS.get(ordenar);
    if (ordem == null) throw ApiException.campo("ordenar", "ORDENACAO_INVALIDA", "Use vencimento,asc, vencimento,desc ou criadoEm,desc.");
    return Resposta.ok(contas.listar(aprovado, status, Pagina.pedido(pagina, tamanho, ordem)));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('financeiro.contapagar.criar')")
  public ResponseEntity<Resposta<ContaPagarSaida>> criar(@Valid @RequestBody ContaPagarEntrada e) {
    ContaPagar c = contas.lancar(e);
    return ResponseEntity.created(URI.create("/api/financeiro/contas-pagar/" + c.getId()))
        .body(Resposta.ok(ContaPagarSaida.de(c), "Conta a pagar lançada."));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.contapagar.ver')")
  public Resposta<ContaPagarSaida> ver(@PathVariable UUID id) {
    return Resposta.ok(ContaPagarSaida.de(contas.obter(id)));
  }

  /** Permissão distinta de quem lança (RN-11). */
  @PatchMapping("/{id}/aprovar")
  @PreAuthorize("hasAuthority('financeiro.contapagar.aprovar')")
  public Resposta<ContaPagarSaida> aprovar(@PathVariable UUID id) {
    return Resposta.ok(ContaPagarSaida.de(contas.aprovar(id)), "Conta aprovada.");
  }

  /** Lacuna no contrato v0.3 (registrar o pagamento, §25), proposta em v0.4. */
  @PostMapping("/{id}/pagamento")
  @PreAuthorize("hasAuthority('financeiro.contapagar.criar')")
  public Resposta<ContaPagarSaida> pagar(@PathVariable UUID id, @Valid @RequestBody(required = false) PagamentoContaEntrada e) {
    return Resposta.ok(ContaPagarSaida.de(contas.registrarPagamento(id, e)), "Pagamento registrado.");
  }
}
