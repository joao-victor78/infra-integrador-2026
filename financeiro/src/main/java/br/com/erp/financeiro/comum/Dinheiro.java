package br.com.erp.financeiro.comum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dinheiro é sempre BigDecimal com 2 casas (RNF-02): nunca float/double. Entrada com mais de 2 casas
 * é rejeitada (@Digits nos DTOs → PRECISAO_EXCEDIDA); arredondamento só em cálculo interno
 * (quantidade × valor unitário, rateio do MRR).
 */
public final class Dinheiro {

  public static final BigDecimal ZERO = new BigDecimal("0.00");

  private Dinheiro() {}

  public static BigDecimal de(BigDecimal v) {
    return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_EVEN);
  }

  public static BigDecimal ouZero(BigDecimal v) {
    return v == null ? ZERO : v;
  }

  public static boolean positivo(BigDecimal v) {
    return v != null && v.signum() > 0;
  }

  public static String brl(BigDecimal v) {
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(de(v)).replace(' ', ' ');
  }
}
