package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.erp.financeiro.pagamentos.provedor.ProvedorSimulado;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** RF-04 (meios de pagamento) e RF-05 (baixa automática por webhook). */
class PagamentosEWebhookTest extends TesteBase {

  @Autowired
  ProvedorSimulado provedor;

  /** RF-04, critérios 1 e 2: Location leva ao pagamento PENDENTE com copia e cola; formato neutro. */
  @Test
  void pixCriadoComLocationEFormatoNeutro() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "1490.00", hoje().plusDays(10));
    MockHttpServletResponse r = mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/pix").with(admin()))
        .andExpect(status().isCreated()).andReturn().getResponse();
    String location = r.getHeader("Location");
    assertThat(location).startsWith("/api/financeiro/pagamentos/");

    String corpo = mvc.perform(get(location).with(admin())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDENTE"))
        .andExpect(jsonPath("$.data.meio").value("PIX"))
        .andExpect(jsonPath("$.data.valor").value(1490.00))
        .andReturn().getResponse().getContentAsString();
    JsonNode dados = ler(corpo).path("data");
    assertThat(dados.path("pixCopiaCola").asText()).startsWith("000201").contains("br.gov.bcb.pix");
    assertThat(dados.path("pixQrCode").asText()).startsWith("data:image/png;base64,");
    List<String> campos = new ArrayList<>();
    dados.fieldNames().forEachRemaining(campos::add);
    assertThat(campos).doesNotContain("provedor", "referenciaProvedor");
  }

  @Test
  void boletoECartaoPeloMesmoAdaptador() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "300.00", hoje().plusDays(10));
    mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/boleto").with(admin()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.boletoLinhaDigitavel").isNotEmpty())
        .andExpect(jsonPath("$.data.boletoUrl").isNotEmpty());
    mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/cartao").with(admin()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.linkPagamentoCartao").isNotEmpty());
  }

  /** RF-04, critério 3: cobrança já PAGO não aceita novo boleto. */
  @Test
  void cobrancaPagaRecusaNovoBoleto() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "100.00", hoje().plusDays(5));
    webhook("ev-" + UUID.randomUUID(), "pagamento.confirmado", cobranca, new BigDecimal("100.00")).andExpect(status().isOk());
    mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/boleto").with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].codigo").value("COBRANCA_NAO_PAGAVEL"));
  }

  /** RF-05, critério 1 (RN-28): o mesmo evento duas vezes → 200 e um pagamento só. */
  @Test
  void webhookRepetidoNaoDuplicaPagamento() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "250.00", hoje().plusDays(5));
    mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/pix").with(admin())).andExpect(status().isCreated());
    String evento = "ev-" + UUID.randomUUID();
    webhook(evento, "pagamento.confirmado", cobranca, new BigDecimal("250.00"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.recebido").value(true));
    webhook(evento, "pagamento.confirmado", cobranca, new BigDecimal("250.00")).andExpect(status().isOk());

    Integer confirmados = jdbc.queryForObject(
        "SELECT count(*) FROM financeiro.pagamentos WHERE cobranca_id = ?::uuid AND status = 'CONFIRMADO'", Integer.class, cobranca);
    assertThat(confirmados).isEqualTo(1);
    assertThat(eventos("financeiro.pagamento.confirmado")).isEqualTo(1);
    mvc.perform(get("/api/financeiro/cobrancas/" + cobranca).with(admin()))
        .andExpect(jsonPath("$.data.status").value("PAGO"))
        .andExpect(jsonPath("$.data.valorPago").value(250.00));
  }

  /** RF-05, critério 2 (RN-27): assinatura inválida → 400 e nada muda. */
  @Test
  void assinaturaInvalidaNaoAlteraNada() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "250.00", hoje().plusDays(5));
    mvc.perform(post("/public/financeiro/webhooks/pagamento").contentType(MediaType.APPLICATION_JSON).content("""
            {"eventoId":"ev-falso","assinatura":"%s","tipo":"pagamento.confirmado","cobrancaId":"%s","valorPago":250.00}"""
            .formatted("0".repeat(64), cobranca)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
    mvc.perform(get("/api/financeiro/cobrancas/" + cobranca).with(admin())).andExpect(jsonPath("$.data.status").value("ABERTO"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM financeiro.webhook_eventos WHERE cobranca_id = ?::uuid",
        Integer.class, cobranca)).isZero();
  }

  /** RF-05, critério 3 (RN-25): acima do limite por IP → 429. */
  @Test
  void limitePorIpResponde429() throws Exception {
    int limite = 1000;
    for (int i = 0; i < limite; i++) {
      mvc.perform(post("/public/financeiro/webhooks/pagamento").with(r -> {
        r.setRemoteAddr("203.0.113.9");
        return r;
      }).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
    mvc.perform(post("/public/financeiro/webhooks/pagamento").with(r -> {
          r.setRemoteAddr("203.0.113.9");
          return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false));
    // outro IP continua sendo atendido
    mvc.perform(post("/public/financeiro/webhooks/pagamento").with(r -> {
          r.setRemoteAddr("203.0.113.10");
          return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  /** RF-05, critério 4 (RN-29): pagamento menor que o saldo → PARCIAL com o restante. */
  @Test
  void pagamentoParcialPorWebhook() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "500.00", hoje().plusDays(5));
    webhook("ev-" + UUID.randomUUID(), "pagamento.confirmado", cobranca, new BigDecimal("200.00")).andExpect(status().isOk());
    mvc.perform(get("/api/financeiro/cobrancas/" + cobranca).with(admin()))
        .andExpect(jsonPath("$.data.status").value("PARCIAL"))
        .andExpect(jsonPath("$.data.saldo").value(300.00));
  }

  @Test
  void estornoSaiComoFalhou() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "100.00", hoje().plusDays(5));
    webhook("ev-" + UUID.randomUUID(), "pagamento.confirmado", cobranca, new BigDecimal("100.00")).andExpect(status().isOk());
    webhook("ev-" + UUID.randomUUID(), "pagamento.estornado", cobranca, new BigDecimal("100.00")).andExpect(status().isOk());
    mvc.perform(get("/api/financeiro/cobrancas/" + cobranca).with(admin()))
        .andExpect(jsonPath("$.data.status").value("ESTORNADO"))
        .andExpect(jsonPath("$.data.saldo").value(100.00));
    assertThat(eventos("financeiro.pagamento.falhou")).isEqualTo(1);
  }

  @Test
  void simuladorConfirmaOPedido() throws Exception {
    String cobranca = criarCobranca(UUID.randomUUID(), "75.50", hoje().plusDays(5));
    String r = mvc.perform(post("/api/financeiro/cobrancas/" + cobranca + "/pagamento/pix").with(admin()))
        .andReturn().getResponse().getContentAsString();
    String pagamento = ler(r).at("/data/pagamentoId").asText();
    mvc.perform(post("/api/financeiro/pagamentos/" + pagamento + "/simular-confirmacao").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMADO"));
    mvc.perform(get("/api/financeiro/cobrancas/" + cobranca + "/pagamentos").with(admin()))
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  private org.springframework.test.web.servlet.ResultActions webhook(String evento, String tipo, String cobranca, BigDecimal valor)
      throws Exception {
    String assinatura = provedor.assinar(evento, tipo, UUID.fromString(cobranca), valor);
    return mvc.perform(post("/public/financeiro/webhooks/pagamento").contentType(MediaType.APPLICATION_JSON).content("""
        {"eventoId":"%s","assinatura":"%s","tipo":"%s","cobrancaId":"%s","valorPago":%s}"""
        .formatted(evento, assinatura, tipo, cobranca, valor.toPlainString())));
  }
}
