package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RF-06 (régua) e RN-06 (job de atraso). */
class ReguaTest extends TesteBase {

  @BeforeEach
  void configurarRegua() throws Exception {
    mvc.perform(put("/api/financeiro/regua").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"etapas":[
              {"diasOffset":-5,"canal":"EMAIL","templateMensagem":"Sua fatura de {valor} vence em {vencimento}."},
              {"diasOffset":1,"canal":"NOTIFICACAO_INTERNA","templateMensagem":"Cobrança {cobranca} em atraso."},
              {"diasOffset":30,"canal":"EMAIL","acao":"marcar crítico","templateMensagem":"Cobrança com 30 dias de atraso."}]}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.etapas.length()").value(3));
  }

  /** RF-06, critérios 1 e 2: D-5 dispara uma vez, mesmo com o job rodando duas vezes no dia. */
  @Test
  void lembreteDisparaUmaVezSo() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "300.00", hoje().plusDays(5));
    mvc.perform(post("/api/financeiro/regua/executar").with(admin())).andExpect(jsonPath("$.data.disparos").value(1));
    mvc.perform(post("/api/financeiro/regua/executar").with(admin())).andExpect(jsonPath("$.data.disparos").value(0));
    mvc.perform(get("/api/financeiro/cobrancas/" + id + "/disparos").with(admin()))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].diasOffset").value(-5))
        .andExpect(jsonPath("$.data[0].situacao").value("SOLICITADO"));
    assertThat(eventos("identity.email.enviar")).isEqualTo(1);
  }

  /** RF-06, critério 3 (RN-35): cobrança paga antes de D+1 não recebe etapa de atraso. */
  @Test
  void cobrancaPagaNaoRecebeAvisoDeAtraso() throws Exception {
    String paga = criarCobranca(UUID.randomUUID(), "100.00", hoje().minusDays(1));
    mvc.perform(post("/api/financeiro/cobrancas/" + paga + "/baixas").with(admin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"valor\":100.00}")).andExpect(status().isCreated());
    String aberta = criarCobranca(UUID.randomUUID(), "100.00", hoje().minusDays(1));
    mvc.perform(post("/api/financeiro/regua/executar").with(admin())).andExpect(jsonPath("$.data.disparos").value(1));
    mvc.perform(get("/api/financeiro/cobrancas/" + paga + "/disparos").with(admin())).andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(get("/api/financeiro/cobrancas/" + aberta + "/disparos").with(admin())).andExpect(jsonPath("$.data.length()").value(1));
  }

  /** RN-06: vencida vira ATRASADO e financeiro.pagamento.atrasado sai uma vez por cobrança. */
  @Test
  void atrasoMarcadoEPublicadoUmaVez() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "100.00", hoje().plusDays(3));
    relogio.fixar(hoje().plusDays(10));
    mvc.perform(post("/api/financeiro/regua/executar").with(admin()))
        .andExpect(jsonPath("$.data.marcadasAtrasadas").value(1))
        .andExpect(jsonPath("$.data.eventosDeAtraso").value(1));
    mvc.perform(post("/api/financeiro/regua/executar").with(admin()))
        .andExpect(jsonPath("$.data.eventosDeAtraso").value(0));
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(admin())).andExpect(jsonPath("$.data.status").value("ATRASADO"));
    assertThat(eventos("financeiro.pagamento.atrasado")).isEqualTo(1);
  }

  @Test
  void deslocamentoDuplicadoOuModeloVazioResponde400() throws Exception {
    mvc.perform(put("/api/financeiro/regua").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"etapas":[{"diasOffset":0,"canal":"EMAIL","templateMensagem":"a"},{"diasOffset":0,"canal":"EMAIL","templateMensagem":"b"}]}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].codigo").value("DESLOCAMENTO_DUPLICADO"));
    mvc.perform(put("/api/financeiro/regua").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"etapas":[{"diasOffset":0,"canal":"EMAIL","templateMensagem":"  "}]}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void trocarDeslocamentosEntreEtapasMantemOsIds() throws Exception {
    String r = mvc.perform(get("/api/financeiro/regua").with(usuario(tenant, "financeiro.regua.ver")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var etapas = ler(r).at("/data/etapas");
    String a = etapas.get(0).path("id").asText();
    String b = etapas.get(1).path("id").asText();
    mvc.perform(put("/api/financeiro/regua").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"etapas":[{"id":"%s","diasOffset":1,"canal":"EMAIL","templateMensagem":"x"},
                       {"id":"%s","diasOffset":-5,"canal":"EMAIL","templateMensagem":"y","ativa":false}]}""".formatted(a, b)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.etapas.length()").value(2))
        .andExpect(jsonPath("$.data.etapas[0].id").value(b))
        .andExpect(jsonPath("$.data.etapas[0].ativa").value(false));
  }
}
