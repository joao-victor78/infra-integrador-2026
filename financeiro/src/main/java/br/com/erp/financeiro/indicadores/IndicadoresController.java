package br.com.erp.financeiro.indicadores;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.comum.Relogio;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "mrr-dashboard")
@RestController
@RequestMapping("/api/financeiro")
public class IndicadoresController {

  private final IndicadoresService indicadores;
  private final Relogio relogio;

  public IndicadoresController(IndicadoresService indicadores, Relogio relogio) {
    this.indicadores = indicadores;
    this.relogio = relogio;
  }

  @GetMapping("/mrr")
  @PreAuthorize("hasAuthority('financeiro.dashboard.ver')")
  public Resposta<IndicadoresService.Mrr> mrr(@RequestParam String periodo) {
    return Resposta.ok(indicadores.mrr(periodo(periodo)));
  }

  /** Tabela do MRR por período (tela do dashboard): os N meses até "ate". */
  @GetMapping("/mrr/historico")
  @PreAuthorize("hasAuthority('financeiro.dashboard.ver')")
  public Resposta<List<IndicadoresService.Mrr>> historico(
      @RequestParam(required = false) String ate, @RequestParam(required = false) Integer meses) {
    int n = meses == null ? 6 : meses;
    if (n < 1 || n > 24) throw ApiException.campo("meses", "VALOR_INVALIDO", "Entre 1 e 24.");
    return Resposta.ok(indicadores.historico(ate == null ? relogio.mesAtual() : periodo(ate), n));
  }

  @GetMapping("/dashboard")
  @PreAuthorize("hasAuthority('financeiro.dashboard.ver')")
  public Resposta<IndicadoresService.Dashboard> dashboard() {
    return Resposta.ok(indicadores.dashboard());
  }

  private static YearMonth periodo(String texto) {
    try {
      return YearMonth.parse(texto);
    } catch (DateTimeParseException e) {
      throw ApiException.campo("periodo", "FORMATO_INVALIDO", "Use o formato AAAA-MM.");
    }
  }
}
