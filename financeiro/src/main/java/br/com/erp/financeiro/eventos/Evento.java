package br.com.erp.financeiro.eventos;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de toda mensagem do RabbitMQ, igual em todos os módulos (Contrato §9.7).
 *
 * @param id           chave de idempotência: quem consome ignora um id já processado
 * @param tipo         modulo.entidade.acao, com o verbo no particípio (pedidos à plataforma são a exceção)
 * @param tenantId     a única fonte de tenant numa mensagem, que não carrega token
 * @param usuarioId    quem provocou o fato; nulo em rotina automática
 * @param correlacaoId X-Request-Id da requisição de origem
 */
public record Evento(
    UUID id,
    String tipo,
    int versao,
    UUID tenantId,
    String moduloOrigem,
    OffsetDateTime ocorridoEm,
    UUID usuarioId,
    String correlacaoId,
    Map<String, Object> dados) {}
