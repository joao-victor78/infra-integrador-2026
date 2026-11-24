package br.com.erp.financeiro.inadimplencia;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.inadimplencia.InadimplenciaService.Historico;
import br.com.erp.financeiro.inadimplencia.InadimplenciaService.Painel;
import br.com.erp.financeiro.inadimplencia.InadimplenciaService.PromessaEntrada;
import br.com.erp.financeiro.inadimplencia.InadimplenciaService.PromessaSaida;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "inadimplencia")
@RestController
@RequestMapping("/api/financeiro")
public class InadimplenciaController {

  private final InadimplenciaService inadimplencia;

  public InadimplenciaController(InadimplenciaService inadimplencia) {
    this.inadimplencia = inadimplencia;
  }

  @GetMapping("/inadimplencia")
  @PreAuthorize("hasAuthority('financeiro.inadimplencia.ver')")
  public Resposta<Painel> painel(
      @RequestParam(required = false) String faixa,
      @RequestParam(required = false) String ordenar,
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamanho) {
    FaixaAging f = null;
    if (faixa != null && !faixa.isBlank()) {
      f = FaixaAging.doRotulo(faixa);
      if (f == null) throw ApiException.campo("faixa", "FAIXA_INVALIDA", "Use 1-5, 6-15, 16-30, 31-60, 61-90 ou 90+.");
    }
    InadimplenciaService.Ordem ordem = "valor".equalsIgnoreCase(ordenar) ? InadimplenciaService.Ordem.VALOR
        : InadimplenciaService.Ordem.DIAS;
    Pageable p = Pagina.pedido(pagina, tamanho, Sort.unsorted());
    return Resposta.ok(inadimplencia.painel(f, ordem, p.getPageNumber(), p.getPageSize()));
  }

  @GetMapping("/inadimplencia/empresas/{empresaId}")
  @PreAuthorize("hasAuthority('financeiro.inadimplencia.ver')")
  public Resposta<Historico> historico(@PathVariable UUID empresaId) {
    return Resposta.ok(inadimplencia.historico(empresaId));
  }

  @GetMapping("/cobrancas/{id}/promessas")
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<List<PromessaSaida>> promessas(@PathVariable UUID id) {
    return Resposta.ok(inadimplencia.promessasDaCobranca(id));
  }

  @PostMapping("/cobrancas/{id}/promessas")
  @PreAuthorize("hasAuthority('financeiro.contareceber.editar')")
  public ResponseEntity<Resposta<PromessaSaida>> registrar(@PathVariable UUID id, @Valid @RequestBody PromessaEntrada e) {
    PromessaSaida p = inadimplencia.registrarPromessa(id, e);
    return ResponseEntity.created(URI.create("/api/financeiro/cobrancas/" + id + "/promessas"))
        .body(Resposta.ok(p, "Promessa registrada."));
  }
}
