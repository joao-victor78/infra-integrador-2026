package br.com.erp.financeiro.eventos;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchanges e filas do Financeiro (Contrato §9.7, §13.3). O módulo declara só o que começa com
 * "financeiro."; a exchange de outro módulo é referenciada pelo nome, nunca declarada.
 */
@Configuration
public class TopologiaMensageria {

  public static final String DLX = "financeiro.dlx";
  public static final String FILA_CONTRATO_ASSINADO = "financeiro.contrato-assinado";
  public static final String FILA_CONTRATO_CANCELADO = "financeiro.contrato-cancelado";

  @Bean
  TopicExchange exchangePropria() {
    return new TopicExchange(TiposDeEvento.EXCHANGE_PROPRIA, true, false);
  }

  @Bean
  DirectExchange exchangeDeMensagensComFalha() {
    return new DirectExchange(DLX, true, false);
  }

  @Bean
  Declarables filasDeContratos(DirectExchange exchangeDeMensagensComFalha) {
    return new Declarables(
        fila(FILA_CONTRATO_ASSINADO), dlq(FILA_CONTRATO_ASSINADO),
        BindingBuilder.bind(dlq(FILA_CONTRATO_ASSINADO)).to(exchangeDeMensagensComFalha).with(FILA_CONTRATO_ASSINADO + ".dlq"),
        new Binding(FILA_CONTRATO_ASSINADO, Binding.DestinationType.QUEUE, "contratos.eventos", TiposDeEvento.CONTRATO_ASSINADO, null),
        fila(FILA_CONTRATO_CANCELADO), dlq(FILA_CONTRATO_CANCELADO),
        BindingBuilder.bind(dlq(FILA_CONTRATO_CANCELADO)).to(exchangeDeMensagensComFalha).with(FILA_CONTRATO_CANCELADO + ".dlq"),
        new Binding(FILA_CONTRATO_CANCELADO, Binding.DestinationType.QUEUE, "contratos.eventos", TiposDeEvento.CONTRATO_CANCELADO, null));
  }

  /** Depois de três falhas, a mensagem vai para a .dlq em vez de voltar à fila para sempre. */
  private static Queue fila(String nome) {
    return QueueBuilder.durable(nome).deadLetterExchange(DLX).deadLetterRoutingKey(nome + ".dlq").build();
  }

  private static Queue dlq(String nome) {
    return QueueBuilder.durable(nome + ".dlq").build();
  }
}
