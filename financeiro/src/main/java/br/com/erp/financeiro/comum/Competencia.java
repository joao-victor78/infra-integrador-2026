package br.com.erp.financeiro.comum;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Competência é "AAAA-MM" no banco (char(7), DER). Na API, o contrato a representa como data do
 * primeiro dia do mês ("2026-09-01"); qualquer dia do mês é aceito na entrada.
 */
public final class Competencia {

  private Competencia() {}

  public static String de(LocalDate data) {
    return YearMonth.from(data).toString();
  }

  public static String de(YearMonth mes) {
    return mes.toString();
  }

  public static YearMonth mes(String competencia) {
    return YearMonth.parse(competencia);
  }

  public static LocalDate primeiroDia(String competencia) {
    return competencia == null ? null : YearMonth.parse(competencia).atDay(1);
  }

  public static String somarMeses(String competencia, int meses) {
    return YearMonth.parse(competencia).plusMonths(meses).toString();
  }
}
