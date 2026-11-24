package br.com.erp.financeiro.eventos;

import br.com.erp.financeiro.tenant.TenantContexto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome contratos.contrato.assinado e contratos.contrato.cancelado. A mensagem não carrega token: o
 * tenant vem do envelope e é definido antes da transação (Contrato §6 e §9.7).
 */
@Component
public class ConsumidorContratos {

  private final ProcessadorContratos processador;
  private final ObjectMapper mapper;

  public ConsumidorContratos(ProcessadorContratos processador, ObjectMapper mapper) {
    this.processador = processador;
    this.mapper = mapper;
  }

  @RabbitListener(queues = TopologiaMensageria.FILA_CONTRATO_ASSINADO)
  public void contratoAssinado(Message mensagem) {
    Evento e = ler(mensagem);
    TenantContexto.executar(e.tenantId(), () -> processador.contratoAssinado(e));
  }

  @RabbitListener(queues = TopologiaMensageria.FILA_CONTRATO_CANCELADO)
  public void contratoCancelado(Message mensagem) {
    Evento e = ler(mensagem);
    TenantContexto.executar(e.tenantId(), () -> processador.contratoCancelado(e));
  }

  /** Mensagem fora do envelope não adianta tentar de novo: vai direto para a .dlq. */
  private Evento ler(Message mensagem) {
    try {
      Evento e = mapper.readValue(mensagem.getBody(), Evento.class);
      if (e.id() == null || e.tenantId() == null || e.dados() == null) throw new IOException("envelope incompleto");
      return e;
    } catch (IOException ex) {
      throw new AmqpRejectAndDontRequeueException("Mensagem fora do envelope da §9.7: " + ex.getMessage(), ex);
    }
  }
}
