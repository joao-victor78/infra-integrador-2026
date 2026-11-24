package br.com.erp.financeiro.eventos;

import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.observabilidade.CorrelacaoFiltro;
import br.com.erp.financeiro.seguranca.Usuarios;
import br.com.erp.financeiro.tenant.TenantContexto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Padrão outbox (DER eventos_outbox, Contrato §9.7): o evento é gravado na mesma transação do fato e
 * publicado só depois do commit. Se o broker estiver fora, fica PENDENTE e o publicador tenta de novo.
 */
@Component
public class Outbox {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Relogio relogio;
  private final PublicadorOutbox publicador;
  private final String modulo;

  public Outbox(JdbcTemplate jdbc, ObjectMapper mapper, Relogio relogio, PublicadorOutbox publicador,
      @Value("${financeiro.modulo:financeiro}") String modulo) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.relogio = relogio;
    this.publicador = publicador;
    this.modulo = modulo;
  }

  /** Grava o evento (ou pedido à plataforma) para publicar depois do commit da transação atual. */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID gravar(String tipo, Map<String, Object> dados) {
    UUID tenant = TenantContexto.exigir();
    Evento evento = new Evento(UUID.randomUUID(), tipo, 1, tenant, modulo,
        relogio.agora().atOffset(ZoneOffset.UTC), Usuarios.atual(), MDC.get(CorrelacaoFiltro.CHAVE_MDC), dados);
    String payload;
    try {
      payload = mapper.writeValueAsString(evento);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Evento " + tipo + " não serializável", e);
    }
    Timestamp agora = Timestamp.from(relogio.agora());
    jdbc.update("""
        INSERT INTO financeiro.eventos_outbox (id, tenant_id, routing_key, payload, status, tentativas, criado_em, proxima_tentativa)
        VALUES (?, ?, ?, CAST(? AS jsonb), 'PENDENTE', 0, ?, ?)""",
        evento.id(), tenant, tipo, payload, agora, agora);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        publicador.publicarEmSegundoPlano(evento.id());
      }
    });
    return evento.id();
  }
}
