package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Checklist de conformidade (Contrato §15) e RNF-01, RNF-05, RNF-06, RNF-11. */
class SegurancaEIsolamentoTest extends TesteBase {

  @Test
  void healthResponde200SemToken() throws Exception {
    mvc.perform(get("/api/financeiro/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("UP"));
  }

  @Test
  void semTokenResponde401NoEnvelope() throws Exception {
    mvc.perform(get("/api/financeiro/cobrancas"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.errors").isArray());
  }

  /** RF-01, critério 1. */
  @Test
  void semPermissaoDeEditarResponde403() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "100.00", hoje().plusDays(10));
    mvc.perform(put("/api/financeiro/cobrancas/" + id).with(usuario(tenant, VER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\":\"" + UUID.randomUUID() + "\",\"competencia\":\"2026-11-01\",\"vencimento\":\"2026-12-10\",\"valor\":90.00}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false));
  }

  /** RNF-05: sem permissão é 403 antes de qualquer validação do corpo (não vaza 400). */
  @Test
  void semPermissaoComCorpoInvalidoAindaE403() throws Exception {
    mvc.perform(post("/api/financeiro/cobrancas").with(usuario(tenant, VER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/financeiro/cobrancas").with(usuario(tenant, VER)).contentType(MediaType.APPLICATION_JSON).content("{x"))
        .andExpect(status().isForbidden());
  }

  /** RF-01, critério 2: outro tenant responde 404, nunca 403. */
  @Test
  void cobrancaDeOutroTenantResponde404() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "100.00", hoje().plusDays(10));
    UUID outro = UUID.randomUUID();
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(usuario(outro, VER))).andExpect(status().isNotFound());
    mvc.perform(get("/api/financeiro/cobrancas").with(usuario(outro, VER))).andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(post("/api/financeiro/cobrancas/" + id + "/pagamento/pix").with(usuario(outro, EDITAR)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/financeiro/cobrancas/" + id).with(usuario(tenant, VER))).andExpect(status().isOk());
  }

  @Test
  void tenantIdNoCorpoEIgnorado() throws Exception {
    UUID doCorpo = UUID.randomUUID();
    mvc.perform(post("/api/financeiro/cobrancas").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tenantId":"%s","clienteId":"%s","competencia":"2026-11-01","vencimento":"%s","valor":50.00}"""
                .formatted(doCorpo, UUID.randomUUID(), hoje().plusDays(5))))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/financeiro/cobrancas/")))
        .andExpect(jsonPath("$.data.tenantId").value(tenant.toString()));
    mvc.perform(get("/api/financeiro/cobrancas").with(usuario(doCorpo, VER))).andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void tokenDeServicoUsaTenantDoCabecalho() throws Exception {
    criarCobranca(UUID.randomUUID(), "10.00", hoje().plusDays(3));
    mvc.perform(get("/api/financeiro/cobrancas").with(servico("crm", VER))).andExpect(status().isBadRequest());
    mvc.perform(get("/api/financeiro/cobrancas").with(servico("crm", VER)).header("X-Tenant-Id", tenant.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  void listagemPaginadaComLimiteDe100() throws Exception {
    mvc.perform(get("/api/financeiro/cobrancas?tamanho=101").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].campo").value("tamanho"));
    mvc.perform(get("/api/financeiro/cobrancas?tamanho=100&ordenar=criadoEm,desc").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tamanho").value(100))
        .andExpect(jsonPath("$.data.pagina").value(0))
        .andExpect(jsonPath("$.data.itens").isArray());
  }

  @Test
  void rotaInexistenteResponde404NoEnvelope() throws Exception {
    mvc.perform(get("/api/financeiro/nao-existe").with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void webhookEhPublicoMasNaoAceitaPayloadMalformado() throws Exception {
    mvc.perform(post("/public/financeiro/webhooks/pagamento").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
    assertThat(eventos("financeiro.pagamento.confirmado")).isZero();
  }
}
