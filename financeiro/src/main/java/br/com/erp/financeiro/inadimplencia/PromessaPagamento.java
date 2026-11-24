package br.com.erp.financeiro.inadimplencia;

import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Promessa de pagamento de uma cobrança em atraso (RN-42, desejável). */
@Entity
@Table(name = "promessas_pagamento")
public class PromessaPagamento extends EntidadeBase {

  public enum Status { PENDENTE, CUMPRIDA, QUEBRADA }

  @Column(name = "cobranca_id", nullable = false)
  private UUID cobrancaId;

  @Column(name = "data_prometida", nullable = false)
  private LocalDate dataPrometida;

  @Column(nullable = false)
  private BigDecimal valor;

  private String observacao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDENTE;

  protected PromessaPagamento() {}

  public PromessaPagamento(UUID cobrancaId, LocalDate dataPrometida, BigDecimal valor, String observacao) {
    this.cobrancaId = cobrancaId;
    this.dataPrometida = dataPrometida;
    this.valor = Dinheiro.de(valor);
    this.observacao = observacao;
  }

  public void cumprir() {
    status = Status.CUMPRIDA;
  }

  public void quebrar() {
    status = Status.QUEBRADA;
  }

  public UUID getCobrancaId() {
    return cobrancaId;
  }

  public LocalDate getDataPrometida() {
    return dataPrometida;
  }

  public BigDecimal getValor() {
    return valor;
  }

  public String getObservacao() {
    return observacao;
  }

  public Status getStatus() {
    return status;
  }
}
