package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.comum.Competencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** contratos/financeiro.yaml: ItemRecorrencia, RegraRecorrencia, RegraRecorrenciaEntrada. */
public final class RecorrenciaDtos {

  private RecorrenciaDtos() {}

  public record ItemEntrada(
      @NotNull(message = "o serviço é obrigatório") UUID servicoId,
      @NotBlank(message = "obrigatória") @Size(max = 200, message = "até 200 caracteres") String descricao,
      @NotNull(message = "obrigatória") @Positive(message = "deve ser maior que zero")
      @Digits(integer = 9, fraction = 3, message = "no máximo 3 casas decimais") BigDecimal quantidade,
      @NotBlank(message = "obrigatória") @Size(max = 20, message = "até 20 caracteres") String unidade,
      @NotNull(message = "obrigatório") @PositiveOrZero(message = "não pode ser negativo")
      @Digits(integer = 13, fraction = 2, message = "no máximo 2 casas decimais") BigDecimal valorUnitario,
      @PositiveOrZero(message = "não pode ser negativa") @Digits(integer = 9, fraction = 3, message = "no máximo 3 casas decimais")
      BigDecimal franquia) {}

  /** contratoId é opcional enquanto o módulo Contratos não publica o evento de assinatura (EOR §2.4). */
  public record RegraEntrada(
      UUID contratoId,
      @NotNull(message = "a empresa (clienteId) é obrigatória") UUID clienteId,
      @Size(max = 200, message = "até 200 caracteres") String descricao,
      Periodicidade periodicidade,
      @Min(value = 1, message = "entre 1 e 28") @Max(value = 28, message = "entre 1 e 28") Integer diaGeracao,
      @Min(value = 0, message = "entre 0 e 60") @Max(value = 60, message = "entre 0 e 60") Integer diasParaVencimento,
      MeioPagamento formaPagamento,
      Boolean ativa,
      @NotEmpty(message = "informe ao menos um item") List<@Valid ItemEntrada> itens) {}

  public record ItemSaida(UUID id, UUID servicoId, String descricao, BigDecimal quantidade, String unidade,
      BigDecimal valorUnitario, BigDecimal franquia, BigDecimal total, LocalDate vigenteDesde, LocalDate vigenteAte) {

    static ItemSaida de(RecorrenciaItem i) {
      return new ItemSaida(i.getId(), i.getServicoId(), i.getDescricao(), i.getQuantidade(), i.getUnidade(),
          i.getValorUnitario(), i.getFranquia(), i.total(), Competencia.primeiroDia(i.getVigenteDesde()),
          Competencia.primeiroDia(i.getVigenteAte()));
    }
  }

  public record RegraSaida(
      UUID id,
      UUID tenantId,
      UUID contratoId,
      UUID clienteId,
      String descricao,
      Periodicidade periodicidade,
      int diaGeracao,
      int diasParaVencimento,
      MeioPagamento formaPagamento,
      LocalDate proximaCompetencia,
      BigDecimal valorProximoCiclo,
      List<ItemSaida> itens,
      boolean ativa,
      LocalDate vigenciaInicio,
      LocalDate vigenciaFim,
      OffsetDateTime createdAt) {

    /** {@code itens}: os vigentes na próxima geração (o que será cobrado). */
    static RegraSaida de(Recorrencia r, List<RecorrenciaItem> todosOsItens) {
      List<RecorrenciaItem> vigentes = todosOsItens.stream().filter(i -> i.vigenteEm(r.getProximaCompetencia())).toList();
      BigDecimal valor = vigentes.stream().map(RecorrenciaItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
      return new RegraSaida(r.getId(), r.getTenantId(), r.getContratoId(), r.getEmpresaId(), r.getDescricao(),
          r.getPeriodicidade(), r.getDiaGeracao(), r.getDiasParaVencimento(), r.getFormaPagamento(),
          Competencia.primeiroDia(r.getProximaCompetencia()), valor.setScale(2), vigentes.stream().map(ItemSaida::de).toList(),
          r.isAtiva(), r.getVigenciaInicio(), r.getVigenciaFim(), r.getCreatedAt());
    }
  }
}
