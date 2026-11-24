package br.com.erp.financeiro.regua;

import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.regua.ReguaDtos.Disparo;
import br.com.erp.financeiro.regua.ReguaDtos.Execucao;
import br.com.erp.financeiro.regua.ReguaDtos.Regua;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "regua")
@RestController
@RequestMapping("/api/financeiro")
public class ReguaController {

  private final ReguaService regua;
  private final CobrancaService cobrancas;

  public ReguaController(ReguaService regua, CobrancaService cobrancas) {
    this.regua = regua;
    this.cobrancas = cobrancas;
  }

  @GetMapping("/regua")
  @PreAuthorize("hasAuthority('financeiro.regua.ver')")
  public Resposta<Regua> ver() {
    return Resposta.ok(new Regua(regua.ver()));
  }

  @PutMapping("/regua")
  @PreAuthorize("hasAuthority('financeiro.regua.administrar')")
  public Resposta<Regua> atualizar(@Valid @RequestBody Regua r) {
    return Resposta.ok(new Regua(regua.atualizar(r.etapas())), "Régua atualizada.");
  }

  /** Roda agora o job diário só para o tenant de quem pede (o agendado roda às 7h para todos). */
  @Operation(summary = "Executar agora o job de atraso e régua do tenant")
  @PostMapping("/regua/executar")
  @PreAuthorize("hasAuthority('financeiro.regua.administrar')")
  public Resposta<Execucao> executar() {
    return Resposta.ok(regua.executar());
  }

  /** Histórico de disparos de uma cobrança (RN-32). */
  @GetMapping("/cobrancas/{id}/disparos")
  @PreAuthorize("hasAuthority('financeiro.contareceber.ver')")
  public Resposta<List<Disparo>> disparos(@PathVariable UUID id) {
    cobrancas.obter(id);
    return Resposta.ok(regua.disparosDaCobranca(id));
  }
}
