package br.com.erp.financeiro.cobrancas;

import java.util.EnumSet;
import java.util.Set;

/** RN-02. */
public enum StatusCobranca {
  ABERTO, AGENDADO, PAGO, ATRASADO, CANCELADO, ESTORNADO, PARCIAL;

  /** Ainda há saldo a receber: entram em atraso, régua, inadimplência e status financeiro. */
  public static final Set<StatusCobranca> EM_ABERTO = EnumSet.of(ABERTO, AGENDADO, ATRASADO, PARCIAL);

  /** Aceitam pedido de PIX, boleto, cartão ou baixa manual. */
  public static final Set<StatusCobranca> PAGAVEIS = EnumSet.of(ABERTO, AGENDADO, ATRASADO, PARCIAL);
}
