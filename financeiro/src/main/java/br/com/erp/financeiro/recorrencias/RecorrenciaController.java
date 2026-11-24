package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.CobrancaSaida;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.recorrencias.RecorrenciaDtos.RegraEntrada;
import br.com.erp.financeiro.recorrencias.RecorrenciaDtos.RegraSaida;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "recorrencia")
@RestController
@RequestMapping("/api/financeiro/recorrencias")
public class RecorrenciaController {

  private final RecorrenciaService recorrencias;
  private final Relogio relogio;

  public RecorrenciaController(RecorrenciaService recorrencias, Relogio relogio) {
    this.recorrencias = recorrencias;
    this.relogio = relogio;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('financeiro.recorrencia.ver')")
  public Resposta<Pagina<RegraSaida>> listar(
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamanho,
      @RequestParam(required = false) UUID clienteId,
      @RequestParam(required = false) Boolean ativa) {
    return Resposta.ok(recorrencias.listar(clienteId, ativa,
        Pagina.pedido(pagina, tamanho, Sort.by("createdAt").descending().and(Sort.by("id")))));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('financeiro.recorrencia.administrar')")
  public ResponseEntity<Resposta<RegraSaida>> criar(@Valid @RequestBody RegraEntrada e) {
    RegraSaida r = recorrencias.criar(e);
    return ResponseEntity.created(URI.create("/api/financeiro/recorrencias/" + r.id())).body(Resposta.ok(r, "Regra criada."));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.recorrencia.ver')")
  public Resposta<RegraSaida> ver(@PathVariable UUID id) {
    return Resposta.ok(recorrencias.ver(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('financeiro.recorrencia.administrar')")
  public Resposta<RegraSaida> atualizar(@PathVariable UUID id, @Valid @RequestBody RegraEntrada e) {
    return Resposta.ok(recorrencias.atualizar(id, e), "Regra atualizada: vale a partir da próxima geração.");
  }

  @PostMapping("/{id}/gerar-cobranca")
  @PreAuthorize("hasAuthority('financeiro.recorrencia.administrar')")
  public ResponseEntity<Resposta<CobrancaSaida>> gerar(@PathVariable UUID id) {
    Cobranca c = recorrencias.gerarManual(id);
    return ResponseEntity.created(URI.create("/api/financeiro/cobrancas/" + c.getId()))
        .body(Resposta.ok(CobrancaSaida.de(c, relogio.hoje()), "Cobrança do ciclo gerada."));
  }
}
