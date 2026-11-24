package br.com.erp.financeiro.integracoes;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Relogio;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Status financeiro da empresa para a ficha do CRM (RF-10, Contrato §9.3, §9.4, §10.4). Sempre 200,
 * mesmo sem cobrança (RN-53). Nunca traz valor nem forma de pagamento (RN-55): cabe na permissão fraca.
 * Uma consulta só, também no lote de 100, para caber no timeout de 3 s do consumidor (RN-56).
 */
@Tag(name = "integracoes")
@RestController
public class StatusFinanceiroController {

  static final int MAXIMO_LOTE = 100;
  static final int LIMIAR_CRITICO = 30; // PD-9 / RN-54: alinhado à etapa D+30 da régua

  private final CobrancaRepository cobrancas;
  private final Relogio relogio;

  public StatusFinanceiroController(CobrancaRepository cobrancas, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.relogio = relogio;
  }

  public record StatusFinanceiro(UUID empresaId, String situacao, int cobrancasEmAtraso, Long diasMaiorAtraso,
      LocalDate proximoVencimento) {}

  @GetMapping("/api/financeiro/empresas/{empresaId}/status-financeiro")
  @PreAuthorize("hasAuthority('financeiro.cobranca.ver_resumo')")
  @Transactional(readOnly = true)
  public Resposta<StatusFinanceiro> daEmpresa(@PathVariable UUID empresaId) {
    return Resposta.ok(calcular(List.of(empresaId)).get(0));
  }

  @GetMapping("/api/financeiro/empresas/status-financeiro")
  @PreAuthorize("hasAuthority('financeiro.cobranca.ver_resumo')")
  @Transactional(readOnly = true)
  public Resposta<List<StatusFinanceiro>> emLote(@RequestParam List<String> ids) {
    Set<UUID> unicos = new LinkedHashSet<>();
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      try {
        unicos.add(UUID.fromString(id.trim()));
      } catch (IllegalArgumentException e) {
        throw ApiException.campo("ids", "FORMATO_INVALIDO", "Id inválido: " + id);
      }
    }
    if (unicos.isEmpty()) throw ApiException.campo("ids", "OBRIGATORIO", "Informe ao menos um id.");
    if (unicos.size() > MAXIMO_LOTE) throw ApiException.campo("ids", "LIMITE_EXCEDIDO", "No máximo " + MAXIMO_LOTE + " ids por consulta.");
    return Resposta.ok(calcular(unicos));
  }

  /** RN-54: sem_cobranca · ok (nenhuma vencida) · atencao (até 30 dias) · critico (mais de 30 dias). */
  List<StatusFinanceiro> calcular(Collection<UUID> empresas) {
    LocalDate hoje = relogio.hoje();
    Map<UUID, List<Cobranca>> porEmpresa = cobrancas.daEmpresasComStatus(empresas, StatusCobranca.EM_ABERTO).stream()
        .filter(c -> c.saldo().signum() > 0)
        .collect(Collectors.groupingBy(Cobranca::getEmpresaId));
    List<StatusFinanceiro> lista = new ArrayList<>();
    for (UUID empresa : empresas) {
      List<Cobranca> abertas = porEmpresa.getOrDefault(empresa, List.of());
      if (abertas.isEmpty()) {
        lista.add(new StatusFinanceiro(empresa, "sem_cobranca", 0, null, null));
        continue;
      }
      List<Cobranca> vencidas = abertas.stream().filter(c -> c.getDataVencimento().isBefore(hoje)).toList();
      Long maior = vencidas.isEmpty() ? null : vencidas.stream().mapToLong(c -> c.diasAtraso(hoje)).max().getAsLong();
      LocalDate proximo = abertas.stream().map(Cobranca::getDataVencimento).filter(d -> !d.isBefore(hoje))
          .min(LocalDate::compareTo).orElse(null);
      String situacao = maior == null ? "ok" : maior > LIMIAR_CRITICO ? "critico" : "atencao";
      lista.add(new StatusFinanceiro(empresa, situacao, vencidas.size(), maior, proximo));
    }
    return lista;
  }
}
