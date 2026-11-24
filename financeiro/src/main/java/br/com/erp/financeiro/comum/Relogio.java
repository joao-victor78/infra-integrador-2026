package br.com.erp.financeiro.comum;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Fonte única de "agora" do módulo. Os jobs (atraso, régua, recorrência) dependem da data; centralizar
 * aqui permite simular a passagem do tempo nos testes.
 */
@Component
public class Relogio {

  public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

  private volatile Instant fixo;

  public Instant agora() {
    return fixo != null ? fixo : Instant.now();
  }

  public OffsetDateTime agoraUtc() {
    return OffsetDateTime.ofInstant(agora(), ZoneOffset.UTC);
  }

  /** Data de hoje no fuso de Brasília. */
  public LocalDate hoje() {
    return LocalDate.ofInstant(agora(), BRASILIA);
  }

  public YearMonth mesAtual() {
    return YearMonth.from(hoje());
  }

  /** Fixa o relógio ao meio-dia (Brasília) da data; null volta ao relógio real. Uso em testes. */
  public void fixar(LocalDate data) {
    fixo = data == null ? null : data.atTime(LocalTime.NOON).atZone(BRASILIA).toInstant();
  }
}
