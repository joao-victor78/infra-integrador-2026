package br.com.erp.financeiro.regua;

import br.com.erp.financeiro.tenant.TenantContexto;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job diário: por tenant, marca atrasos e dispara a régua (diagrama de atividades da EOR). */
@Component
public class ReguaJob {

  private static final Logger log = LoggerFactory.getLogger(ReguaJob.class);

  private final ReguaService regua;
  private final JdbcTemplate jdbc;
  private final boolean ativo;

  public ReguaJob(ReguaService regua, JdbcTemplate jdbc, @Value("${financeiro.jobs.ativos:true}") boolean ativo) {
    this.regua = regua;
    this.jdbc = jdbc;
    this.ativo = ativo;
  }

  @Scheduled(cron = "${financeiro.jobs.regua-cron}", zone = "${financeiro.jobs.fuso}")
  public void agendado() {
    if (ativo) executar();
  }

  public void executar() {
    List<UUID> tenants = jdbc.queryForList("""
        SELECT DISTINCT tenant_id FROM financeiro.cobrancas
         WHERE deleted_at IS NULL AND status IN ('ABERTO','AGENDADO','ATRASADO','PARCIAL')
        UNION SELECT DISTINCT tenant_id FROM financeiro.promessas_pagamento WHERE status = 'PENDENTE'""", UUID.class);
    for (UUID tenant : tenants) {
      try {
        ReguaDtos.Execucao r = TenantContexto.calcular(tenant, regua::executar);
        log.info("Régua do tenant {}: {}", tenant, r);
      } catch (RuntimeException e) {
        log.error("Régua do tenant {} falhou: {}", tenant, e.getMessage(), e);
      }
    }
  }
}
