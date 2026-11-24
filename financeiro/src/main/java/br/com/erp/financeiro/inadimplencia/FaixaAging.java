package br.com.erp.financeiro.inadimplencia;

import com.fasterxml.jackson.annotation.JsonValue;

/** RN-38: faixas em dias após o vencimento. */
public enum FaixaAging {
  ATE_5("1-5", 1, 5),
  ATE_15("6-15", 6, 15),
  ATE_30("16-30", 16, 30),
  ATE_60("31-60", 31, 60),
  ATE_90("61-90", 61, 90),
  ACIMA_90("90+", 91, Long.MAX_VALUE);

  private final String rotulo;
  private final long de;
  private final long ate;

  FaixaAging(String rotulo, long de, long ate) {
    this.rotulo = rotulo;
    this.de = de;
    this.ate = ate;
  }

  @JsonValue
  public String rotulo() {
    return rotulo;
  }

  public static FaixaAging de(long diasAtraso) {
    for (FaixaAging f : values()) if (diasAtraso >= f.de && diasAtraso <= f.ate) return f;
    throw new IllegalArgumentException("Sem atraso: " + diasAtraso);
  }

  public static FaixaAging doRotulo(String rotulo) {
    for (FaixaAging f : values()) if (f.rotulo.equals(rotulo)) return f;
    return null;
  }
}
