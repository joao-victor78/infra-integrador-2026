package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.tenant.TenantContexto;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job diário de geração das cobranças recorrentes (Sistema/jobs, RF-03), tenant a tenant. */
@Component
public class RecorrenciaJob {

  private static final Logger log = LoggerFactory.getLogger(RecorrenciaJob.class);

  private final RecorrenciaService recorrencias;
  private final JdbcTemplate jdbc;
  private final boolean ativo;

  public RecorrenciaJob(RecorrenciaService recorrencias, JdbcTemplate jdbc, @Value("${financeiro.jobs.ativos:true}") boolean ativo) {
    this.recorrencias = recorrencias;
    this.jdbc = jdbc;
    this.ativo = ativo;
  }

  @Scheduled(cron = "${financeiro.jobs.recorrencia-cron}", zone = "${financeiro.jobs.fuso}")
  public void agendado() {
    if (ativo) executar();
  }

  /** @return cobranças geradas em todos os tenants */
  public int executar() {
    int total = 0;
    List<UUID> tenants = jdbc.queryForList(
        "SELECT DISTINCT tenant_id FROM financeiro.recorrencias WHERE ativa AND deleted_at IS NULL", UUID.class);
    for (UUID tenant : tenants) total += TenantContexto.calcular(tenant, this::executarNoTenant);
    if (total > 0) log.info("Recorrência: {} cobrança(s) gerada(s).", total);
    return total;
  }

  public int executarNoTenant() {
    int total = 0;
    for (UUID regra : recorrencias.pendentesDeGeracao()) {
      try {
        total += recorrencias.processarRegraNoJob(regra); // uma transação por regra
      } catch (RuntimeException e) {
        log.warn("Recorrência {} não gerada: {}", regra, e.getMessage());
      }
    }
    return total;
  }
}
