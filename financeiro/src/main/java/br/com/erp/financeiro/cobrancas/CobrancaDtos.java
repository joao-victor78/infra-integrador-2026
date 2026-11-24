package br.com.erp.financeiro.cobrancas;

import br.com.erp.financeiro.comum.Competencia;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Formatos de entrada e saída de contas a receber (contratos/financeiro.yaml: Cobranca, CobrancaEntrada). */
public final class CobrancaDtos {

  private CobrancaDtos() {}

  static final String MSG_CASAS = "no máximo 2 casas decimais";

  /** clienteId é o empresaId do CRM (EOR §2.4). tenantId no corpo é ignorado. */
  public record CobrancaEntrada(
      @NotNull(message = "a empresa (clienteId) é obrigatória") UUID clienteId,
      UUID contratoId,
      @NotNull(message = "obrigatória") LocalDate competencia,
      @NotNull(message = "obrigatório") LocalDate vencimento,
      @NotNull(message = "obrigatório") @Positive(message = "deve ser maior que zero")
      @Digits(integer = 13, fraction = 2, message = MSG_CASAS) BigDecimal valor,
      @PositiveOrZero(message = "não pode ser negativo") @Digits(integer = 13, fraction = 2, message = MSG_CASAS) BigDecimal desconto,
      @PositiveOrZero(message = "não pode ser negativo") @Digits(integer = 13, fraction = 2, message = MSG_CASAS) BigDecimal juros,
      @PositiveOrZero(message = "não pode ser negativo") @Digits(integer = 13, fraction = 2, message = MSG_CASAS) BigDecimal multa,
      @Size(max = 200, message = "até 200 caracteres") String descricao,
      MeioPagamento formaPagamento) {}

  public record CobrancaSaida(
      UUID id,
      UUID tenantId,
      UUID clienteId,
      UUID contratoId,
      UUID recorrenciaId,
      @JsonFormat(pattern = "yyyy-MM-dd") LocalDate competencia,
      LocalDate vencimento,
      String descricao,
      BigDecimal valor,
      BigDecimal desconto,
      BigDecimal juros,
      BigDecimal multa,
      BigDecimal valorPago,
      BigDecimal saldo,
      MeioPagamento formaPagamento,
      StatusCobranca status,
      long diasAtraso,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public static CobrancaSaida de(Cobranca c, LocalDate hoje) {
      return new CobrancaSaida(c.getId(), c.getTenantId(), c.getEmpresaId(), c.getContratoId(), c.getRecorrenciaId(),
          Competencia.primeiroDia(c.getCompetencia()), c.getDataVencimento(), c.getDescricao(), c.getValor(),
          c.getDesconto(), c.getJuros(), c.getMulta(), c.getValorPago(), c.saldo(), c.getFormaPagamento(),
          c.getStatus(), c.emAberto() ? c.diasAtraso(hoje) : 0, c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt());
    }
  }

  /** Baixa manual (RF-12): transferência ou outro recebimento fora do provedor. */
  public record BaixaManualEntrada(
      @NotNull(message = "obrigatório") @Positive(message = "deve ser maior que zero")
      @Digits(integer = 13, fraction = 2, message = MSG_CASAS) BigDecimal valor,
      LocalDate dataPagamento,
      UUID comprovanteId) {}
}
