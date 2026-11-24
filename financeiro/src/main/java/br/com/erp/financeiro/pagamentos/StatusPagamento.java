package br.com.erp.financeiro.pagamentos;

/** RN-21. Só CONFIRMADO abate o saldo da cobrança. */
public enum StatusPagamento {
  PENDENTE, PROCESSANDO, CONFIRMADO, ESTORNADO, FALHOU, EXPIRADO
}
