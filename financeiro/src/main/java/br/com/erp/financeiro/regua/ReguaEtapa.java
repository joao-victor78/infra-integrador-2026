package br.com.erp.financeiro.regua;

import br.com.erp.financeiro.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Etapa configurável da régua (RN-31): deslocamento em dias, canal e modelo de mensagem. */
@Entity
@Table(name = "regua_etapas")
public class ReguaEtapa extends EntidadeBase {

  public enum Canal { EMAIL, WHATSAPP, NOTIFICACAO_INTERNA }

  @Column(name = "deslocamento_dias", nullable = false)
  private int deslocamentoDias;

  private String acao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Canal canal;

  @Column(name = "modelo_mensagem", nullable = false)
  private String modeloMensagem;

  @Column(nullable = false)
  private boolean ativa = true;

  protected ReguaEtapa() {}

  public ReguaEtapa(int deslocamentoDias, String acao, Canal canal, String modeloMensagem, boolean ativa) {
    configurar(deslocamentoDias, acao, canal, modeloMensagem, ativa);
  }

  public void configurar(int deslocamentoDias, String acao, Canal canal, String modeloMensagem, boolean ativa) {
    this.deslocamentoDias = deslocamentoDias;
    this.acao = acao;
    this.canal = canal;
    this.modeloMensagem = modeloMensagem;
    this.ativa = ativa;
  }

  /** Usado na troca de deslocamentos entre etapas, para não violar o índice único no meio do caminho. */
  void deslocamentoProvisorio(int valor) {
    this.deslocamentoDias = valor;
  }

  /** A etapa vence quando vencimento + deslocamento = hoje. */
  public boolean vence(LocalDate vencimento, LocalDate hoje) {
    return vencimento.plusDays(deslocamentoDias).equals(hoje);
  }

  /** "D-5", "D0", "D+30". */
  public String rotulo() {
    return deslocamentoDias == 0 ? "D0" : deslocamentoDias > 0 ? "D+" + deslocamentoDias : "D" + deslocamentoDias;
  }

  public int getDeslocamentoDias() {
    return deslocamentoDias;
  }

  public String getAcao() {
    return acao;
  }

  public Canal getCanal() {
    return canal;
  }

  public String getModeloMensagem() {
    return modeloMensagem;
  }

  public boolean isAtiva() {
    return ativa;
  }
}
