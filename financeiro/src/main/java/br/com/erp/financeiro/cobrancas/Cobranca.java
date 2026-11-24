package br.com.erp.financeiro.cobrancas;

import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Conta a receber: valor devido por uma empresa (CRM), com vencimento (RF-01, RN-01). */
@Entity
@Table(name = "cobrancas")
public class Cobranca extends EntidadeBase {

  @Column(name = "empresa_id", nullable = false)
  private UUID empresaId;

  @Column(name = "contrato_id")
  private UUID contratoId;

  @Column(name = "recorrencia_id")
  private UUID recorrenciaId;

  @Column(nullable = false, length = 7)
  private String competencia;

  private String descricao;

  @Column(name = "data_vencimento", nullable = false)
  private LocalDate dataVencimento;

  @Column(nullable = false)
  private BigDecimal valor;

  @Column(nullable = false)
  private BigDecimal desconto = Dinheiro.ZERO;

  @Column(nullable = false)
  private BigDecimal juros = Dinheiro.ZERO;

  @Column(nullable = false)
  private BigDecimal multa = Dinheiro.ZERO;

  @Column(name = "valor_pago", nullable = false)
  private BigDecimal valorPago = Dinheiro.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(name = "forma_pagamento")
  private MeioPagamento formaPagamento;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StatusCobranca status = StatusCobranca.ABERTO;

  @Column(name = "atraso_publicado_em")
  private OffsetDateTime atrasoPublicadoEm;

  protected Cobranca() {}

  public static Cobranca nova(UUID empresaId, UUID contratoId, UUID recorrenciaId, String competencia, String descricao,
      LocalDate vencimento, BigDecimal valor, BigDecimal desconto, BigDecimal juros, BigDecimal multa,
      MeioPagamento forma) {
    Cobranca c = new Cobranca();
    c.empresaId = empresaId;
    c.contratoId = contratoId;
    c.recorrenciaId = recorrenciaId;
    c.competencia = competencia;
    c.descricao = descricao;
    c.dataVencimento = vencimento;
    c.valor = Dinheiro.de(valor);
    c.desconto = Dinheiro.de(desconto);
    c.juros = Dinheiro.de(juros);
    c.multa = Dinheiro.de(multa);
    c.formaPagamento = forma;
    return c;
  }

  /** Saldo devedor: valor − desconto + juros + multa − já pago. Nunca negativo. */
  public BigDecimal saldo() {
    BigDecimal s = valor.subtract(desconto).add(juros).add(multa).subtract(valorPago);
    return s.signum() < 0 ? Dinheiro.ZERO : Dinheiro.de(s);
  }

  public BigDecimal totalDevido() {
    return Dinheiro.de(valor.subtract(desconto).add(juros).add(multa));
  }

  public long diasAtraso(LocalDate hoje) {
    return Math.max(0, ChronoUnit.DAYS.between(dataVencimento, hoje));
  }

  public boolean emAberto() {
    return getDeletedAt() == null && StatusCobranca.EM_ABERTO.contains(status);
  }

  /** RN-05: valor só muda em ABERTO ou AGENDADO. */
  public boolean podeEditarValor() {
    return status == StatusCobranca.ABERTO || status == StatusCobranca.AGENDADO;
  }

  public void editar(UUID empresaId, UUID contratoId, String competencia, String descricao, LocalDate vencimento,
      BigDecimal valor, BigDecimal desconto, BigDecimal juros, BigDecimal multa, MeioPagamento forma) {
    this.empresaId = empresaId;
    this.contratoId = contratoId;
    this.competencia = competencia;
    this.descricao = descricao;
    this.dataVencimento = vencimento;
    this.valor = Dinheiro.de(valor);
    this.desconto = Dinheiro.de(desconto);
    this.juros = Dinheiro.de(juros);
    this.multa = Dinheiro.de(multa);
    this.formaPagamento = forma;
    // Novo vencimento no futuro tira a cobrança do atraso.
  }

  /** RN-29 / RN-61: pagamento igual ao saldo leva a PAGO; menor, a PARCIAL. */
  public StatusCobranca aplicarPagamento(BigDecimal valorRecebido) {
    valorPago = Dinheiro.de(valorPago.add(valorRecebido));
    status = saldo().signum() == 0 ? StatusCobranca.PAGO : StatusCobranca.PARCIAL;
    return status;
  }

  /** Estorno informado pelo provedor: devolve o valor ao saldo. */
  public StatusCobranca estornar(BigDecimal valorEstornado, LocalDate hoje) {
    BigDecimal novo = valorPago.subtract(valorEstornado);
    valorPago = novo.signum() < 0 ? Dinheiro.ZERO : Dinheiro.de(novo);
    if (valorPago.signum() == 0) status = StatusCobranca.ESTORNADO;
    else status = dataVencimento.isBefore(hoje) ? StatusCobranca.ATRASADO : StatusCobranca.PARCIAL;
    return status;
  }

  /** RN-06. */
  public void marcarAtrasada() {
    status = StatusCobranca.ATRASADO;
  }

  public void marcarAtrasoPublicado(OffsetDateTime quando) {
    atrasoPublicadoEm = quando;
  }

  /** RN-04: cancelar é exclusão lógica; a cobrança sai da listagem padrão e o histórico fica. */
  public void cancelar() {
    status = StatusCobranca.CANCELADO;
    excluir();
  }

  /** Volta a ABERTO quando a edição leva o vencimento para hoje ou depois. */
  public void reavaliarAtraso(LocalDate hoje) {
    if (status == StatusCobranca.ATRASADO && !dataVencimento.isBefore(hoje)) {
      status = valorPago.signum() > 0 ? StatusCobranca.PARCIAL : StatusCobranca.ABERTO;
    }
  }

  /** Instantâneo para a auditoria (valor anterior e novo, RN-64). */
  public Map<String, Object> instantaneo() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("empresaId", empresaId);
    m.put("contratoId", contratoId);
    m.put("competencia", competencia);
    m.put("descricao", descricao);
    m.put("vencimento", dataVencimento);
    m.put("valor", valor);
    m.put("desconto", desconto);
    m.put("juros", juros);
    m.put("multa", multa);
    m.put("valorPago", valorPago);
    m.put("formaPagamento", formaPagamento);
    m.put("status", status);
    return m;
  }

  public UUID getEmpresaId() {
    return empresaId;
  }

  public UUID getContratoId() {
    return contratoId;
  }

  public UUID getRecorrenciaId() {
    return recorrenciaId;
  }

  public String getCompetencia() {
    return competencia;
  }

  public String getDescricao() {
    return descricao;
  }

  public LocalDate getDataVencimento() {
    return dataVencimento;
  }

  public BigDecimal getValor() {
    return valor;
  }

  public BigDecimal getDesconto() {
    return desconto;
  }

  public BigDecimal getJuros() {
    return juros;
  }

  public BigDecimal getMulta() {
    return multa;
  }

  public BigDecimal getValorPago() {
    return valorPago;
  }

  public MeioPagamento getFormaPagamento() {
    return formaPagamento;
  }

  public StatusCobranca getStatus() {
    return status;
  }

  public OffsetDateTime getAtrasoPublicadoEm() {
    return atrasoPublicadoEm;
  }
}
