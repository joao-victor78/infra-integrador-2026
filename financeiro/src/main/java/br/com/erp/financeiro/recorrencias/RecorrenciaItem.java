package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Item de uma regra (serviço × quantidade × valor unitário), com vigência por competência: um aditivo
 * cria itens novos a partir da próxima geração e encerra os anteriores (RN-17).
 */
@Entity
@Table(name = "recorrencia_itens")
public class RecorrenciaItem extends EntidadeBase {

  @Column(name = "recorrencia_id", nullable = false)
  private UUID recorrenciaId;

  @Column(name = "servico_id", nullable = false)
  private UUID servicoId;

  @Column(nullable = false)
  private String descricao;

  @Column(nullable = false)
  private BigDecimal quantidade;

  @Column(nullable = false)
  private String unidade;

  @Column(name = "valor_unitario", nullable = false)
  private BigDecimal valorUnitario;

  private BigDecimal franquia;

  @Column(name = "vigente_desde", nullable = false, length = 7)
  private String vigenteDesde;

  @Column(name = "vigente_ate", length = 7)
  private String vigenteAte;

  protected RecorrenciaItem() {}

  public RecorrenciaItem(UUID recorrenciaId, UUID servicoId, String descricao, BigDecimal quantidade, String unidade,
      BigDecimal valorUnitario, BigDecimal franquia, String vigenteDesde) {
    this.recorrenciaId = recorrenciaId;
    this.servicoId = servicoId;
    this.descricao = descricao;
    this.quantidade = quantidade;
    this.unidade = unidade;
    this.valorUnitario = Dinheiro.de(valorUnitario);
    this.franquia = franquia;
    this.vigenteDesde = vigenteDesde;
  }

  public boolean vigenteEm(String competencia) {
    return getDeletedAt() == null && vigenteDesde.compareTo(competencia) <= 0
        && (vigenteAte == null || vigenteAte.compareTo(competencia) >= 0);
  }

  /** Quantidade × valor unitário, arredondado a centavos (cálculo interno, não entrada do usuário). */
  public BigDecimal total() {
    return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_EVEN);
  }

  public void encerrarEm(String competencia) {
    vigenteAte = competencia;
  }

  public UUID getRecorrenciaId() {
    return recorrenciaId;
  }

  public UUID getServicoId() {
    return servicoId;
  }

  public String getDescricao() {
    return descricao;
  }

  public BigDecimal getQuantidade() {
    return quantidade;
  }

  public String getUnidade() {
    return unidade;
  }

  public BigDecimal getValorUnitario() {
    return valorUnitario;
  }

  public BigDecimal getFranquia() {
    return franquia;
  }

  public String getVigenteDesde() {
    return vigenteDesde;
  }

  public String getVigenteAte() {
    return vigenteAte;
  }
}
