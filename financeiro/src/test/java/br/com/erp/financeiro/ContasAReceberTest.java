package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RF-01 (contas a receber), RF-12 (baixa manual) e RF-13 (auditoria). */
class ContasAReceberTest extends TesteBase {

  @Test
  void lancarPublicaCobrancaCriadaEGuardaHistorico() throws Exception {
    criarCobranca(UUID.randomUUID(), "1490.00", hoje().plusDays(10));
    assertThat(eventos("financeiro.cobranca.criada")).isEqualTo(1);
  }

  /** RF-01, critério 4 (RN-07): 3 casas decimais → 400 PRECISAO_EXCEDIDA e nada gravado. */
  @Test
  void valorComTresCasasDecimaisERejeitado() throws Exception {
    mvc.perform(post("/api/financeiro/cobrancas").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"clienteId":"%s","competencia":"2026-11-01","vencimento":"%s","valor":100.005}"""
                .formatted(UUID.randomUUID(), hoje().plusDays(3))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].campo").value("valor"))
        .andExpect(jsonPath("$.errors[0].codigo").value("PRECISAO_EXCEDIDA"));
    mvc.perform(get("/api/financeiro/cobrancas").with(admin())).andExpect(jsonPath("$.data.total").value(0));
  }

  /** RF-01, critério 5 (RN-03): sem empresa → 400. */
  @Test
  void lancamentoSemEmpresaResponde400() throws Exception {
    mvc.perform(post("/api/financeiro/cobrancas").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"competencia\":\"2026-11-01\",\"vencimento\":\"2026-11-10\",\"valor\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].campo").value("clienteId"));
    mvc.perform(get("/api/financeiro/cobrancas").with(admin())).andExpect(jsonPath("$.data.total").value(0));
  }

  /** RF-01, critério 3 (RN-05): com pagamento parcial, editar o valor original → 409. */
  @Test
  void cobrancaParcialNaoEditaValorOriginal() throws Exception {
    UUID empresa = UUID.randomUUID();
    String id = criarCobranca(empresa, "500.00", hoje().plusDays(10));
    baixar(id, "200.00");
    String base = "{\"clienteId\":\"%s\",\"competencia\":\"%s\",\"vencimento\":\"%s\",\"valor\":%s}";
    mvc.perform(put("/api/financeiro/cobrancas/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted(empresa, hoje().withDayOfMonth(1), hoje().plusDays(10), "450.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].codigo").value("EDICAO_NAO_PERMITIDA"));
    // o saldo em aberto continua editável: novo vencimento, mesmo valor
    mvc.perform(put("/api/financeiro/cobrancas/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted(empresa, hoje().withDayOfMonth(1), hoje().plusDays(20), "500.00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.saldo").value(300.00));
  }

  /** RF-12, critério 1: transferência do valor total → PAGO e evento de pagamento publicado. */
  @Test
  void baixaManualTotalQuitaACobranca() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "500.00", hoje().plusDays(10));
    baixar(id, "500.00");
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(admin()))
        .andExpect(jsonPath("$.data.status").value("PAGO"))
        .andExpect(jsonPath("$.data.saldo").value(0.00));
    assertThat(eventos("financeiro.pagamento.confirmado")).isEqualTo(1);
    assertThat(eventos("identity.timeline.registrar")).isEqualTo(1);
  }

  /** RF-12, critério 2: baixa menor que o saldo → PARCIAL com o restante. */
  @Test
  void baixaManualParcialDeixaSaldo() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "500.00", hoje().plusDays(10));
    String r = baixar(id, "200.00");
    assertThat(ler(r).at("/data/meio").asText()).isEqualTo("TRANSFERENCIA");
    assertThat(ler(r).at("/data/status").asText()).isEqualTo("CONFIRMADO");
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(admin()))
        .andExpect(jsonPath("$.data.status").value("PARCIAL"))
        .andExpect(jsonPath("$.data.saldo").value(300.00));
    mvc.perform(post("/api/financeiro/cobrancas/" + id + "/baixas").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"valor\":300.01}"))
        .andExpect(status().isUnprocessableEntity());
  }

  /** RN-04: cancelar é exclusão lógica; sai da listagem padrão e aparece com status=CANCELADO. */
  @Test
  void cancelarTiraDaListagemPadrao() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "80.00", hoje().plusDays(10));
    mvc.perform(delete("/api/financeiro/cobrancas/" + id).with(admin())).andExpect(status().isOk());
    mvc.perform(get("/api/financeiro/cobrancas").with(admin())).andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/api/financeiro/cobrancas?status=CANCELADO").with(admin())).andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(admin()))
        .andExpect(jsonPath("$.data.status").value("CANCELADO"))
        .andExpect(jsonPath("$.data.deletedAt").isNotEmpty());
  }

  /** RF-13, critério 1: alteração de R$ 1.000,00 para R$ 900,00 fica na auditoria com usuário e valores. */
  @Test
  void auditoriaRegistraValorAnteriorENovo() throws Exception {
    UUID empresa = UUID.randomUUID();
    UUID usuario = UUID.randomUUID();
    String id = criarCobranca(empresa, "1000.00", hoje().plusDays(10));
    mvc.perform(put("/api/financeiro/cobrancas/" + id).with(usuarioComId(tenant, usuario, EDITAR))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\":\"%s\",\"competencia\":\"%s\",\"vencimento\":\"%s\",\"valor\":900.00}"
                .formatted(empresa, hoje().withDayOfMonth(1), hoje().plusDays(10))))
        .andExpect(status().isOk());
    String r = mvc.perform(get("/api/financeiro/auditoria?entidade=cobranca&registroId=" + id).with(admin()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    JsonNode edicao = null;
    for (JsonNode n : ler(r).at("/data/itens")) if ("EDITAR".equals(n.path("acao").asText())) edicao = n;
    assertThat(edicao).isNotNull();
    assertThat(edicao.path("usuarioId").asText()).isEqualTo(usuario.toString());
    assertThat(edicao.path("dataHora").asText()).isNotBlank();
    assertThat(edicao.at("/valorAnterior/valor").decimalValue()).isEqualByComparingTo("1000.00");
    assertThat(edicao.at("/valorNovo/valor").decimalValue()).isEqualByComparingTo("900.00");
  }

  /** RF-13, critério 2 (RN-67): o banco recusa apagar ou alterar a auditoria. */
  @Test
  void bancoRecusaApagarAuditoria() throws Exception {
    criarCobranca(UUID.randomUUID(), "10.00", hoje().plusDays(1));
    assertThatThrownBy(() -> jdbc.update("DELETE FROM financeiro.auditoria WHERE tenant_id = ?", tenant))
        .rootCause().hasMessageContaining("somente inserção");
    assertThatThrownBy(() -> jdbc.update("UPDATE financeiro.auditoria SET acao = 'X' WHERE tenant_id = ?", tenant))
        .rootCause().hasMessageContaining("somente inserção");
  }

  @Test
  void auditoriaExigePermissaoPropria() throws Exception {
    mvc.perform(get("/api/financeiro/auditoria").with(usuario(tenant, VER))).andExpect(status().isForbidden());
  }

  private String baixar(String id, String valor) throws Exception {
    return mvc.perform(post("/api/financeiro/cobrancas/" + id + "/baixas").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"valor\":" + valor + "}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }
}
