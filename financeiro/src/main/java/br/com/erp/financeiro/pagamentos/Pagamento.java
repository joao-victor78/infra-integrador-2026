package br.com.erp.financeiro.pagamentos;

import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.EntidadeBase;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tentativa de pagamento de uma cobrança (DER pagamentos). Nunca guarda dado de cartão (RN-22). */
@Entity
@Table(name = "pagamentos")
public class Pagamento extends EntidadeBase {

  @Column(name = "cobranca_id", nullable = false)
  private UUID cobrancaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MeioPagamento meio;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StatusPagamento status = StatusPagamento.PENDENTE;

  @Column(nullable = false)
  private BigDecimal valor;

  private String provedor;

  @Column(name = "referencia_provedor")
  private String referenciaProvedor;

  @Column(name = "pix_copia_cola")
  private String pixCopiaCola;

  @Column(name = "boleto_linha_digitavel")
  private String boletoLinhaDigitavel;

  @Column(name = "boleto_url")
  private String boletoUrl;

  @Column(name = "link_pagamento")
  private String linkPagamento;

  @Column(name = "expira_em")
  private OffsetDateTime expiraEm;

  @Column(name = "confirmado_em")
  private OffsetDateTime confirmadoEm;

  @Column(name = "comprovante_id")
  private UUID comprovanteId;

  protected Pagamento() {}

  /** Pedido feito ao provedor (PIX, boleto ou cartão), aguardando confirmação. */
  public static Pagamento pedido(UUID cobrancaId, MeioPagamento meio, BigDecimal valor, String provedor) {
    Pagamento p = new Pagamento();
    p.cobrancaId = cobrancaId;
    p.meio = meio;
    p.valor = Dinheiro.de(valor);
    p.provedor = provedor;
    return p;
  }

  /** Guarda a resposta neutra do adaptador (RN-19): só referência e dados de pagamento, nunca cartão. */
  public void registrarResposta(ProvedorPagamento.Cobrado c) {
    referenciaProvedor = c.referencia();
    pixCopiaCola = c.pixCopiaCola();
    boletoLinhaDigitavel = c.boletoLinhaDigitavel();
    boletoUrl = c.boletoUrl();
    linkPagamento = c.linkPagamento();
    expiraEm = c.expiraEm();
  }

  /** Baixa manual (RF-12, RN-60): pagamento recebido fora do provedor, já confirmado. */
  public static Pagamento manual(UUID cobrancaId, BigDecimal valor, OffsetDateTime quando, UUID comprovanteId) {
    Pagamento p = new Pagamento();
    p.cobrancaId = cobrancaId;
    p.meio = MeioPagamento.TRANSFERENCIA;
    p.valor = Dinheiro.de(valor);
    p.provedor = "manual";
    p.status = StatusPagamento.CONFIRMADO;
    p.confirmadoEm = quando;
    p.comprovanteId = comprovanteId;
    return p;
  }

  /** O provedor pode confirmar um valor diferente do pedido (pagamento parcial). */
  public void confirmar(BigDecimal valorRecebido, OffsetDateTime quando) {
    valor = Dinheiro.de(valorRecebido);
    status = StatusPagamento.CONFIRMADO;
    confirmadoEm = quando;
  }

  public void falhar() {
    status = StatusPagamento.FALHOU;
  }

  public void estornar() {
    status = StatusPagamento.ESTORNADO;
  }

  public void expirar() {
    status = StatusPagamento.EXPIRADO;
  }

  public boolean pendente() {
    return status == StatusPagamento.PENDENTE || status == StatusPagamento.PROCESSANDO;
  }

  public Map<String, Object> instantaneo() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("cobrancaId", cobrancaId);
    m.put("meio", meio);
    m.put("status", status);
    m.put("valor", valor);
    m.put("provedor", provedor);
    m.put("referenciaProvedor", referenciaProvedor);
    m.put("confirmadoEm", confirmadoEm);
    m.put("comprovanteId", comprovanteId);
    return m;
  }

  public UUID getCobrancaId() {
    return cobrancaId;
  }

  public MeioPagamento getMeio() {
    return meio;
  }

  public StatusPagamento getStatus() {
    return status;
  }

  public BigDecimal getValor() {
    return valor;
  }

  public String getProvedor() {
    return provedor;
  }

  public String getReferenciaProvedor() {
    return referenciaProvedor;
  }

  public String getPixCopiaCola() {
    return pixCopiaCola;
  }

  public String getBoletoLinhaDigitavel() {
    return boletoLinhaDigitavel;
  }

  public String getBoletoUrl() {
    return boletoUrl;
  }

  public String getLinkPagamento() {
    return linkPagamento;
  }

  public OffsetDateTime getExpiraEm() {
    return expiraEm;
  }

  public OffsetDateTime getConfirmadoEm() {
    return confirmadoEm;
  }

  public UUID getComprovanteId() {
    return comprovanteId;
  }
}
