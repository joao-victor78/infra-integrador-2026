package br.com.erp.financeiro.contaspagar;

import br.com.erp.financeiro.comum.Competencia;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** contratos/financeiro.yaml: ContaPagar, ContaPagarEntrada. */
public final class ContaPagarDtos {

  private ContaPagarDtos() {}

  public record ContaPagarEntrada(
      @NotBlank(message = "obrigatório") @Size(max = 150, message = "até 150 caracteres") String fornecedorNome,
      @Size(max = 18, message = "até 18 caracteres") String fornecedorDocumento,
      @Size(max = 60, message = "até 60 caracteres") String categoria,
      @Size(max = 60, message = "até 60 caracteres") String centroCusto,
      @NotNull(message = "obrigatória") LocalDate competencia,
      @NotNull(message = "obrigatório") LocalDate vencimento,
      @NotNull(message = "obrigatório") @Positive(message = "deve ser maior que zero")
      @Digits(integer = 13, fraction = 2, message = "no máximo 2 casas decimais") BigDecimal valor,
      @Size(max = 60, message = "até 60 caracteres") String documento,
      @Size(max = 500, message = "até 500 caracteres") String observacoes) {}

  public record PagamentoContaEntrada(
      @Positive(message = "deve ser maior que zero")
      @Digits(integer = 13, fraction = 2, message = "no máximo 2 casas decimais") BigDecimal valorPago,
      LocalDate dataPagamento) {}

  public record ContaPagarSaida(
      UUID id,
      UUID tenantId,
      String fornecedorNome,
      String fornecedorDocumento,
      String categoria,
      String centroCusto,
      LocalDate competencia,
      LocalDate vencimento,
      BigDecimal valor,
      String documento,
      String observacoes,
      ContaPagar.Status status,
      boolean aprovado,
      UUID aprovadoPor,
      OffsetDateTime aprovadoEm,
      LocalDate dataPagamento,
      BigDecimal valorPago,
      UUID createdBy,
      OffsetDateTime createdAt) {

    public static ContaPagarSaida de(ContaPagar c) {
      return new ContaPagarSaida(c.getId(), c.getTenantId(), c.getFornecedorNome(), c.getFornecedorDocumento(),
          c.getCategoria(), c.getCentroCusto(), Competencia.primeiroDia(c.getCompetencia()), c.getDataVencimento(),
          c.getValor(), c.getDocumento(), c.getObservacoes(), c.getStatus(), c.aprovada(), c.getAprovadoPor(),
          c.getAprovadoEm(), c.getDataPagamento(), c.getValorPago(), c.getCreatedBy(), c.getCreatedAt());
    }
  }
}
