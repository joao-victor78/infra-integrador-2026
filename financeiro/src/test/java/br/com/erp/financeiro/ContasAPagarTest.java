package br.com.erp.financeiro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RF-02: contas a pagar. */
class ContasAPagarTest extends TesteBase {

  static final String CP_CRIAR = "financeiro.contapagar.criar";
  static final String CP_VER = "financeiro.contapagar.ver";
  static final String CP_APROVAR = "financeiro.contapagar.aprovar";

  /** RF-02, critério 1: sem financeiro.contapagar.aprovar → 403. */
  @Test
  void aprovarSemPermissaoResponde403() throws Exception {
    String id = lancar();
    mvc.perform(patch("/api/financeiro/contas-pagar/" + id + "/aprovar").with(usuario(tenant, CP_CRIAR, CP_VER)))
        .andExpect(status().isForbidden());
  }

  /** RF-02, critério 2 (RN-11): constam quem aprovou e quando. */
  @Test
  void aprovacaoRegistraQuemEQuando() throws Exception {
    String id = lancar();
    UUID gestor = UUID.randomUUID();
    mvc.perform(patch("/api/financeiro/contas-pagar/" + id + "/aprovar").with(usuarioComId(tenant, gestor, CP_APROVAR)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/financeiro/contas-pagar/" + id).with(usuario(tenant, CP_VER)))
        .andExpect(jsonPath("$.data.aprovado").value(true))
        .andExpect(jsonPath("$.data.status").value("APROVADA"))
        .andExpect(jsonPath("$.data.aprovadoPor").value(gestor.toString()))
        .andExpect(jsonPath("$.data.aprovadoEm").isNotEmpty());
  }

  /** RF-02, critério 3 (RN-12): conta não aprovada não aceita pagamento e continua pendente. */
  @Test
  void pagamentoSoDepoisDaAprovacao() throws Exception {
    String id = lancar();
    mvc.perform(post("/api/financeiro/contas-pagar/" + id + "/pagamento").with(usuario(tenant, CP_CRIAR))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].codigo").value("CONTA_NAO_APROVADA"));
    mvc.perform(get("/api/financeiro/contas-pagar/" + id).with(usuario(tenant, CP_VER)))
        .andExpect(jsonPath("$.data.status").value("PENDENTE"));

    mvc.perform(patch("/api/financeiro/contas-pagar/" + id + "/aprovar").with(usuario(tenant, CP_APROVAR))).andExpect(status().isOk());
    mvc.perform(post("/api/financeiro/contas-pagar/" + id + "/pagamento").with(usuario(tenant, CP_CRIAR))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAGA"))
        .andExpect(jsonPath("$.data.valorPago").value(1250.40));
  }

  @Test
  void filtroPorAprovado() throws Exception {
    lancar();
    String aprovada = lancar();
    mvc.perform(patch("/api/financeiro/contas-pagar/" + aprovada + "/aprovar").with(usuario(tenant, CP_APROVAR)));
    mvc.perform(get("/api/financeiro/contas-pagar?aprovado=false").with(usuario(tenant, CP_VER)))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/financeiro/contas-pagar?aprovado=true").with(usuario(tenant, CP_VER)))
        .andExpect(jsonPath("$.data.total").value(1));
  }

  private String lancar() throws Exception {
    String r = mvc.perform(post("/api/financeiro/contas-pagar").with(usuario(tenant, CP_CRIAR)).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fornecedorNome":"Distribuidora XYZ","fornecedorDocumento":"12.345.678/0001-90","categoria":"Infraestrutura",
                 "competencia":"%s","vencimento":"%s","valor":1250.40,"documento":"NF-4521"}"""
                .formatted(hoje().withDayOfMonth(1), hoje().plusDays(7))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(r, "$.data.id");
  }
}
