package br.com.erp.financeiro.pagamentos.provedor;

import br.com.erp.financeiro.comum.Relogio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provedor simulado (RN-20): devolve o mesmo formato que um adaptador real devolveria. O PIX copia e cola
 * é um BR Code válido (pode ser lido por app de banco); boleto e link de cartão são fictícios.
 *
 * Assinatura do webhook: HMAC-SHA256, em hexadecimal, de "eventoId|tipo|cobrancaId|valorPago", com o
 * segredo compartilhado (PROVEDOR_WEBHOOK_SEGREDO). valorPago com 2 casas, ou vazio se ausente.
 */
@Component
public class ProvedorSimulado implements ProvedorPagamento {

  public static final String NOME = "simulado";

  private final Relogio relogio;
  private final String segredo;
  private final String chavePix;
  private final String nomeRecebedor;
  private final String cidadeRecebedor;
  private final Duration validade;

  public ProvedorSimulado(Relogio relogio,
      @Value("${financeiro.webhook.segredo}") String segredo,
      @Value("${financeiro.provedor.pix-chave}") String chavePix,
      @Value("${financeiro.provedor.pix-nome}") String nomeRecebedor,
      @Value("${financeiro.provedor.pix-cidade}") String cidadeRecebedor,
      @Value("${financeiro.provedor.validade-horas:72}") long validadeHoras) {
    this.relogio = relogio;
    this.segredo = segredo;
    this.chavePix = chavePix;
    this.nomeRecebedor = nomeRecebedor;
    this.cidadeRecebedor = cidadeRecebedor;
    this.validade = Duration.ofHours(validadeHoras);
  }

  @Override
  public String nome() {
    return NOME;
  }

  @Override
  public Cobrado createPix(Solicitacao s) {
    String txid = BrCode.gerarTxid();
    String copiaCola = BrCode.gerar(chavePix, s.valor(), txid, nomeRecebedor, cidadeRecebedor);
    return new Cobrado("pix-" + txid, copiaCola, null, null, null, relogio.agoraUtc().plus(validade));
  }

  @Override
  public Cobrado createBoleto(Solicitacao s) {
    String ref = "bol-" + s.pagamentoId().toString().replace("-", "").substring(0, 20);
    return new Cobrado(ref, null, linhaDigitavel(s), "https://provedor.simulado/boletos/" + ref, null,
        relogio.agoraUtc().plus(validade));
  }

  @Override
  public Cobrado createCardPayment(Solicitacao s) {
    String ref = "car-" + s.pagamentoId().toString().replace("-", "").substring(0, 20);
    return new Cobrado(ref, null, null, null, "https://provedor.simulado/pagar/" + ref, relogio.agoraUtc().plus(validade));
  }

  @Override
  public boolean refund(String referencia, BigDecimal valor) {
    return true;
  }

  @Override
  public SituacaoNoProvedor getPayment(String referencia) {
    return SituacaoNoProvedor.DESCONHECIDO; // o simulado não guarda estado: quem avisa é o webhook
  }

  @Override
  public boolean cancelPayment(String referencia) {
    return true;
  }

  @Override
  public EventoProvedor processWebhook(WebhookRecebido w) {
    if (w.assinatura() == null || !MessageDigest.isEqual(
        assinar(w.eventoId(), w.tipo(), w.cobrancaId(), w.valorPago()).getBytes(StandardCharsets.UTF_8),
        w.assinatura().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
      throw new AssinaturaInvalidaException();
    }
    TipoEventoProvedor tipo = switch (w.tipo()) {
      case "pagamento.confirmado" -> TipoEventoProvedor.CONFIRMADO;
      case "pagamento.estornado" -> TipoEventoProvedor.ESTORNADO;
      case "pagamento.falhou" -> TipoEventoProvedor.FALHOU;
      default -> throw new IllegalArgumentException("tipo");
    };
    return new EventoProvedor(w.eventoId(), tipo, w.cobrancaId(), w.pagamentoId(), w.valorPago(),
        w.dataPagamento() != null ? w.dataPagamento() : relogio.agoraUtc());
  }

  /** Assinatura esperada (também usada pelo simulador de pagamento e pelos testes). */
  public String assinar(String eventoId, String tipo, UUID cobrancaId, BigDecimal valorPago) {
    String base = eventoId + "|" + tipo + "|" + cobrancaId + "|"
        + (valorPago == null ? "" : valorPago.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC indisponível", e);
    }
  }

  /** 47 dígitos no formato de linha digitável de boleto (banco 000 = fictício). */
  private static String linhaDigitavel(Solicitacao s) {
    String valor = String.format("%010d", s.valor().movePointRight(2).longValueExact());
    String id = s.pagamentoId().toString().replaceAll("[^0-9]", "") + "0000000000000000000000000";
    String base = "0009" + id.substring(0, 28) + "1" + "0000" + valor; // 4 + 28 + 1 + 4 + 10 = 47
    return base.substring(0, 5) + "." + base.substring(5, 10) + " " + base.substring(10, 15) + "." + base.substring(15, 21)
        + " " + base.substring(21, 26) + "." + base.substring(26, 32) + " " + base.charAt(32) + " " + base.substring(33, 47);
  }
}
