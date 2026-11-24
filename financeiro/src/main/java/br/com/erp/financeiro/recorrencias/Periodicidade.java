package br.com.erp.financeiro.recorrencias;

/** RN-16. */
public enum Periodicidade {
  MENSAL(1), TRIMESTRAL(3), ANUAL(12);

  private final int meses;

  Periodicidade(int meses) {
    this.meses = meses;
  }

  public int meses() {
    return meses;
  }
}
