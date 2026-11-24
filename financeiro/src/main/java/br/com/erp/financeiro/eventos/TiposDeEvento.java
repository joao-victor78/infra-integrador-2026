package br.com.erp.financeiro.eventos;

/** Chaves de roteamento publicadas e consumidas (contratos/financeiro.asyncapi.yaml, EOR §7). */
public final class TiposDeEvento {

  public static final String EXCHANGE_PROPRIA = "financeiro.eventos";
  public static final String EXCHANGE_PLATAFORMA = "identity.entrada";

  // publicados pelo Financeiro
  public static final String COBRANCA_CRIADA = "financeiro.cobranca.criada";
  public static final String PAGAMENTO_CONFIRMADO = "financeiro.pagamento.confirmado";
  public static final String PAGAMENTO_ATRASADO = "financeiro.pagamento.atrasado";
  public static final String PAGAMENTO_FALHOU = "financeiro.pagamento.falhou";

  // pedidos à plataforma (contratos/identity.asyncapi.yaml)
  public static final String TIMELINE_REGISTRAR = "identity.timeline.registrar";
  public static final String NOTIFICACAO_CRIAR = "identity.notificacao.criar";
  public static final String EMAIL_ENVIAR = "identity.email.enviar";

  // consumidos do módulo Contratos (Grupo 5 ainda não publicou o AsyncAPI)
  public static final String CONTRATO_ASSINADO = "contratos.contrato.assinado";
  public static final String CONTRATO_CANCELADO = "contratos.contrato.cancelado";

  private TiposDeEvento() {}

  /** Cada módulo publica só na própria exchange; pedidos à plataforma vão para identity.entrada. */
  public static String exchangePara(String routingKey) {
    return routingKey.startsWith("identity.") ? EXCHANGE_PLATAFORMA : EXCHANGE_PROPRIA;
  }
}
