package br.com.erp.financeiro.eventos;

import br.com.erp.financeiro.comum.Relogio;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos do outbox no RabbitMQ: logo depois do commit (em segundo plano, para a requisição
 * não esperar o broker) e, a cada 15 s, o que ficou pendente. Só marca PUBLICADO com a confirmação do
 * broker; três falhas (com espera de 1, 5 e 25 min) marcam FALHOU (EOR, diagrama da régua).
 */
@Component
public class PublicadorOutbox {

  private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);
  static final int MAX_TENTATIVAS = 3;

  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;
  private final Relogio relogio;
  private final String usuarioDoBroker;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "outbox-publicador");
    t.setDaemon(true);
    return t;
  });

  public PublicadorOutbox(JdbcTemplate jdbc, RabbitTemplate rabbit, Relogio relogio,
      @Value("${spring.rabbitmq.username}") String usuarioDoBroker) {
    this.jdbc = jdbc;
    this.rabbit = rabbit;
    this.relogio = relogio;
    this.usuarioDoBroker = usuarioDoBroker;
  }

  public void publicarEmSegundoPlano(UUID id) {
    executor.submit(() -> publicar(id));
  }

  @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT20S")
  public void publicarPendentes() {
    List<UUID> ids = jdbc.queryForList("""
        SELECT id FROM financeiro.eventos_outbox
         WHERE status = 'PENDENTE' AND proxima_tentativa <= ? ORDER BY criado_em LIMIT 200""",
        UUID.class, Timestamp.from(relogio.agora()));
    ids.forEach(this::publicar);
  }

  /** Publica um evento; seguro chamar duas vezes (o UPDATE só vale para quem ainda está PENDENTE). */
  synchronized void publicar(UUID id) {
    List<String[]> linhas = jdbc.query(
        "SELECT routing_key, payload::text FROM financeiro.eventos_outbox WHERE id = ? AND status = 'PENDENTE'",
        (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, id);
    if (linhas.isEmpty()) return;
    String routingKey = linhas.get(0)[0];
    Message mensagem = MessageBuilder.withBody(linhas.get(0)[1].getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .setContentEncoding("UTF-8")
        .setMessageId(id.toString())
        .setType(routingKey)
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        // §9.7 (v0.7): user_id = usuário da conexão (mq_financeiro); a plataforma recusa pedido sem ele
        .setUserId(usuarioDoBroker)
        .build();
    try {
      rabbit.invoke(ops -> {
        ops.send(TiposDeEvento.exchangePara(routingKey), routingKey, mensagem);
        ops.waitForConfirmsOrDie(5_000);
        return null;
      });
      jdbc.update("UPDATE financeiro.eventos_outbox SET status = 'PUBLICADO', publicado_em = ?, ultimo_erro = NULL WHERE id = ?",
          Timestamp.from(relogio.agora()), id);
    } catch (RuntimeException e) {
      Integer feitas = jdbc.queryForObject("SELECT tentativas FROM financeiro.eventos_outbox WHERE id = ?", Integer.class, id);
      long espera = (long) Math.pow(5, feitas == null ? 0 : feitas); // 1, 5 e 25 minutos
      String erro = e.getClass().getSimpleName() + ": " + e.getMessage();
      if (erro.length() > 500) erro = erro.substring(0, 500);
      jdbc.update("""
          UPDATE financeiro.eventos_outbox
             SET tentativas = tentativas + 1,
                 ultimo_erro = ?,
                 status = CASE WHEN tentativas + 1 >= ? THEN 'FALHOU' ELSE 'PENDENTE' END,
                 proxima_tentativa = ?
           WHERE id = ?""",
          erro, MAX_TENTATIVAS, Timestamp.from(relogio.agora().plus(Duration.ofMinutes(espera))), id);
      log.warn("Evento {} ({}) não publicado: {}", routingKey, id, erro);
    }
  }

  @PreDestroy
  void encerrar() {
    executor.shutdown();
  }
}
