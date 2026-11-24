package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.comum.Competencia;
import br.com.erp.financeiro.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Regra de cobrança recorrente de uma empresa (RF-03). O valor do ciclo vem sempre dos itens (RN-14). */
@Entity
@Table(name = "recorrencias")
public class Recorrencia extends EntidadeBase {

  @Column(name = "empresa_id", nullable = false)
  private UUID empresaId;

  @Column(name = "contrato_id")
  private UUID contratoId;

  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Periodicidade periodicidade = Periodicidade.MENSAL;

  @Column(name = "dia_geracao", nullable = false)
  private int diaGeracao;

  @Column(name = "dias_para_vencimento", nullable = false)
  private int diasParaVencimento = 10;

  @Column(name = "proxima_competencia", nullable = false, length = 7)
  private String proximaCompetencia;

  @Enumerated(EnumType.STRING)
  @Column(name = "forma_pagamento")
  private MeioPagamento formaPagamento;

  @Column(nullable = false)
  private boolean ativa = true;

  @Column(name = "vigencia_inicio", nullable = false)
  private LocalDate vigenciaInicio;

  @Column(name = "vigencia_fim")
  private LocalDate vigenciaFim;

  protected Recorrencia() {}

  public Recorrencia(UUID empresaId, UUID contratoId, String primeiraCompetencia, LocalDate vigenciaInicio) {
    this.empresaId = empresaId;
    this.contratoId = contratoId;
    this.proximaCompetencia = primeiraCompetencia;
    this.vigenciaInicio = vigenciaInicio;
  }

  public void configurar(UUID empresaId, UUID contratoId, String descricao, Periodicidade periodicidade, int diaGeracao,
      int diasParaVencimento, MeioPagamento formaPagamento) {
    this.empresaId = empresaId;
    this.contratoId = contratoId;
    this.descricao = descricao;
    this.periodicidade = periodicidade;
    this.diaGeracao = diaGeracao;
    this.diasParaVencimento = diasParaVencimento;
    this.formaPagamento = formaPagamento;
  }

  /** Data em que o ciclo da competência deve ser gerado. */
  public LocalDate dataDeGeracao(String competencia) {
    return Competencia.mes(competencia).atDay(diaGeracao);
  }

  public LocalDate vencimentoDe(String competencia) {
    return dataDeGeracao(competencia).plusDays(diasParaVencimento);
  }

  /** Depois de gerar um ciclo, a próxima competência anda uma periodicidade a partir dele. */
  public void avancarDepoisDe(String competenciaGerada) {
    String seguinte = Competencia.somarMeses(competenciaGerada, periodicidade.meses());
    if (seguinte.compareTo(proximaCompetencia) > 0) proximaCompetencia = seguinte;
  }

  public void ativar() {
    ativa = true;
    vigenciaFim = null;
  }

  /** Contrato cancelado ou regra encerrada: não gera mais e entra como churn (RN-43). */
  public void encerrar(LocalDate fim) {
    ativa = false;
    vigenciaFim = fim;
  }

  public Map<String, Object> instantaneo() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("empresaId", empresaId);
    m.put("contratoId", contratoId);
    m.put("descricao", descricao);
    m.put("periodicidade", periodicidade);
    m.put("diaGeracao", diaGeracao);
    m.put("diasParaVencimento", diasParaVencimento);
    m.put("proximaCompetencia", proximaCompetencia);
    m.put("formaPagamento", formaPagamento);
    m.put("ativa", ativa);
    m.put("vigenciaFim", vigenciaFim);
    return m;
  }

  public UUID getEmpresaId() {
    return empresaId;
  }

  public UUID getContratoId() {
    return contratoId;
  }

  public String getDescricao() {
    return descricao;
  }

  public Periodicidade getPeriodicidade() {
    return periodicidade;
  }

  public int getDiaGeracao() {
    return diaGeracao;
  }

  public int getDiasParaVencimento() {
    return diasParaVencimento;
  }

  public String getProximaCompetencia() {
    return proximaCompetencia;
  }

  public MeioPagamento getFormaPagamento() {
    return formaPagamento;
  }

  public boolean isAtiva() {
    return ativa;
  }

  public LocalDate getVigenciaInicio() {
    return vigenciaInicio;
  }

  public LocalDate getVigenciaFim() {
    return vigenciaFim;
  }
}
