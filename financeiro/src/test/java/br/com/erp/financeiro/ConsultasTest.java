package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.erp.financeiro.eventos.ConsumidorContratos;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** RF-07 (inadimplência), RF-08 (MRR), RF-09 (busca), RF-10 (status financeiro) e RF-11 (exportação). */
class ConsultasTest extends TesteBase {

  static final String RESUMO = "financeiro.cobranca.ver_resumo";

  @Autowired
  ConsumidorContratos consumidor;

  /** RF-07, critério 1: vencida há 20 dias aparece só na faixa 16-30. */
  @Test
  void agingPorFaixa() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "890.00", hoje().minusDays(20));
    String r = mvc.perform(get("/api/financeiro/inadimplencia").with(usuario(tenant, "financeiro.inadimplencia.ver")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.itens[0].cobrancaId").value(id))
        .andExpect(jsonPath("$.data.itens[0].faixa").value("16-30"))
        .andExpect(jsonPath("$.data.itens[0].diasAtraso").value(20))
        .andReturn().getResponse().getContentAsString();
    JsonNode faixas = ler(r).at("/data/totalPorFaixa");
    assertThat(faixas.path("16-30").decimalValue()).isEqualByComparingTo("890.00");
    for (String f : List.of("1-5", "6-15", "31-60", "61-90", "90+")) assertThat(faixas.path(f).decimalValue()).isZero();
  }

  /** RF-07, critério 2 (RN-39): PARCIAL entra pelo saldo. */
  @Test
  void parcialEntraPeloSaldo() throws Exception {
    String id = criarCobranca(UUID.randomUUID(), "400.00", hoje().minusDays(3));
    mvc.perform(post("/api/financeiro/cobrancas/" + id + "/baixas").with(admin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"valor\":300.00}")).andExpect(status().isCreated());
    mvc.perform(get("/api/financeiro/inadimplencia").with(admin()))
        .andExpect(jsonPath("$.data.itens[0].saldoDevedor").value(100.00))
        .andExpect(jsonPath("$.data.totalPorFaixa['1-5']").value(100.00));
  }

  /** RF-08, critério 1: primeira cobrança recorrente no mês entra como Novo. */
  @Test
  void mrrNovo() throws Exception {
    relogio.fixar(LocalDate.of(2026, 12, 10));
    UUID empresa = UUID.randomUUID();
    String regra = criarRegraEGerar(empresa, null);
    assertThat(regra).isNotBlank();
    mvc.perform(get("/api/financeiro/mrr?periodo=2026-12").with(usuario(tenant, "financeiro.dashboard.ver")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mrrNovo").value(1500.00))
        .andExpect(jsonPath("$.data.mrrExpansion").value(0.00))
        .andExpect(jsonPath("$.data.mrrTotal").value(1500.00))
        .andExpect(jsonPath("$.data.arr").value(18000.00));
  }

  /** RF-08, critério 2: contrato cancelado no mês → o valor recorrente anterior entra como Churned. */
  @Test
  void mrrChurnedNoMesDoCancelamento() throws Exception {
    relogio.fixar(LocalDate.of(2026, 11, 10));
    UUID contrato = UUID.randomUUID();
    criarRegraEGerar(UUID.randomUUID(), contrato);
    relogio.fixar(LocalDate.of(2026, 12, 3));
    String cancelado = """
        {"id":"%s","tipo":"contratos.contrato.cancelado","versao":1,"tenantId":"%s","moduloOrigem":"contratos",
         "ocorridoEm":"2026-12-03T10:00:00Z","dados":{"contratoId":"%s","canceladoEm":"2026-12-03"}}"""
        .formatted(UUID.randomUUID(), tenant, contrato);
    consumidor.contratoCancelado(MessageBuilder.withBody(cancelado.getBytes(StandardCharsets.UTF_8)).build());
    mvc.perform(get("/api/financeiro/mrr?periodo=2026-12").with(admin()))
        .andExpect(jsonPath("$.data.mrrChurned").value(1500.00))
        .andExpect(jsonPath("$.data.mrrInicial").value(1500.00))
        .andExpect(jsonPath("$.data.mrrNetNew").value(-1500.00))
        .andExpect(jsonPath("$.data.logoChurn").value(100.00));
  }

  @Test
  void dashboardDoFinanceiro() throws Exception {
    criarCobranca(UUID.randomUUID(), "120.00", hoje());
    criarCobranca(UUID.randomUUID(), "80.00", hoje().minusDays(2));
    mvc.perform(get("/api/financeiro/dashboard").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.aReceberHoje").value(120.00))
        .andExpect(jsonPath("$.data.inadimplenciaTotal").value(80.00));
  }

  /** RF-09, critério 1: q com 1 caractere → 400. */
  @Test
  void buscaComUmCaractere() throws Exception {
    mvc.perform(get("/api/financeiro/busca?q=a").with(admin())).andExpect(status().isBadRequest());
  }

  /** RF-09, critério 2 (RN-51): sem contareceber.ver, nenhuma cobrança; contas a pagar aparecem. */
  @Test
  void buscaRespeitaPermissaoPorTipo() throws Exception {
    mvc.perform(post("/api/financeiro/cobrancas").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
        {"clienteId":"%s","competencia":"2026-11-01","vencimento":"%s","valor":10.00,"descricao":"Serviços Zeta Ltda"}"""
        .formatted(UUID.randomUUID(), hoje().plusDays(1)))).andExpect(status().isCreated());
    mvc.perform(post("/api/financeiro/contas-pagar").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
        {"fornecedorNome":"Zeta Suprimentos","competencia":"2026-11-01","vencimento":"%s","valor":20.00}"""
        .formatted(hoje().plusDays(1)))).andExpect(status().isCreated());

    mvc.perform(get("/api/financeiro/busca?q=zeta").with(usuario(tenant, "financeiro.acessar", "financeiro.contapagar.ver")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].rota").value(org.hamcrest.Matchers.startsWith("/contas-pagar/")));
    mvc.perform(get("/api/financeiro/busca?q=zeta").with(admin()))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].titulo").isNotEmpty())
        .andExpect(jsonPath("$.data[0].subtitulo").isNotEmpty());
    mvc.perform(get("/api/financeiro/busca?q=zeta").with(usuario(tenant, VER))).andExpect(status().isForbidden());
  }

  /** RF-10, critério 1: empresa sem cobrança → 200 com sem_cobranca. */
  @Test
  void statusSemCobranca() throws Exception {
    UUID empresa = UUID.randomUUID();
    mvc.perform(get("/api/financeiro/empresas/" + empresa + "/status-financeiro").with(usuario(tenant, RESUMO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.situacao").value("sem_cobranca"))
        .andExpect(jsonPath("$.data.cobrancasEmAtraso").value(0));
  }

  /** RF-10, critérios 2 e 4: vencida há 45 dias → critico; vendedor recebe situação e atraso sem valores. */
  @Test
  void statusCriticoSemValores() throws Exception {
    UUID empresa = UUID.randomUUID();
    criarCobranca(empresa, "999.99", hoje().minusDays(45));
    criarCobranca(empresa, "10.00", hoje().plusDays(7));
    String r = mvc.perform(get("/api/financeiro/empresas/" + empresa + "/status-financeiro").with(usuario(tenant, RESUMO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.situacao").value("critico"))
        .andExpect(jsonPath("$.data.cobrancasEmAtraso").value(1))
        .andExpect(jsonPath("$.data.diasMaiorAtraso").value(45))
        .andExpect(jsonPath("$.data.proximoVencimento").value(hoje().plusDays(7).toString()))
        .andReturn().getResponse().getContentAsString();
    assertThat(r).doesNotContain("999").doesNotContain("valor").doesNotContain("formaPagamento");
    mvc.perform(get("/api/financeiro/empresas/" + empresa + "/status-financeiro").with(usuario(tenant, "financeiro.acessar")))
        .andExpect(status().isForbidden());
  }

  @Test
  void statusAtencaoEOk() throws Exception {
    UUID atencao = UUID.randomUUID();
    UUID ok = UUID.randomUUID();
    criarCobranca(atencao, "10.00", hoje().minusDays(30));
    criarCobranca(ok, "10.00", hoje().plusDays(1));
    mvc.perform(get("/api/financeiro/empresas/status-financeiro?ids=" + atencao + "," + ok).with(usuario(tenant, RESUMO)))
        .andExpect(jsonPath("$.data[0].situacao").value("atencao"))
        .andExpect(jsonPath("$.data[1].situacao").value("ok"));
  }

  /** RF-10, critério 3: 101 ids no lote → 400. */
  @Test
  void loteAcimaDe100Ids() throws Exception {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 101; i++) ids.add(UUID.randomUUID().toString());
    mvc.perform(get("/api/financeiro/empresas/status-financeiro?ids=" + String.join(",", ids)).with(usuario(tenant, RESUMO)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/financeiro/empresas/status-financeiro?ids=" + String.join(",", ids.subList(0, 100)))
            .with(usuario(tenant, RESUMO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(100));
  }

  /** RF-11, critério 1: sem financeiro.relatorio.exportar → 403. */
  @Test
  void exportarSemPermissao() throws Exception {
    mvc.perform(get("/api/financeiro/relatorios/cobrancas/exportar?formato=csv").with(usuario(tenant, VER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false));
  }

  /** RF-11, critério 2 (RN-58): o CSV traz só cobranças do tenant de quem pede. */
  @Test
  void exportarCsvSoDoTenant() throws Exception {
    String minha = criarCobranca(UUID.randomUUID(), "321.00", hoje().plusDays(3));
    UUID outroTenant = UUID.randomUUID();
    mvc.perform(post("/api/financeiro/cobrancas").with(usuario(outroTenant, CRIAR)).contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"clienteId":"%s","competencia":"2026-11-01","vencimento":"%s","valor":777.00}"""
            .formatted(UUID.randomUUID(), hoje().plusDays(3)))).andExpect(status().isCreated());
    String csv = mvc.perform(get("/api/financeiro/relatorios/cobrancas/exportar?formato=csv")
            .with(usuario(tenant, "financeiro.relatorio.exportar")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).contains(minha).contains("321,00").doesNotContain("777,00");
  }

  @Test
  void exportarXlsxEPdf() throws Exception {
    criarCobranca(UUID.randomUUID(), "50.00", hoje().plusDays(3));
    byte[] xlsx = mvc.perform(get("/api/financeiro/relatorios/cobrancas/exportar?formato=xlsx").with(admin()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
    byte[] pdf = mvc.perform(get("/api/financeiro/relatorios/cobrancas/exportar?formato=pdf").with(admin()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    mvc.perform(get("/api/financeiro/relatorios/cobrancas/exportar?formato=doc").with(admin())).andExpect(status().isBadRequest());
  }

  private String criarRegraEGerar(UUID empresa, UUID contrato) throws Exception {
    String corpo = RecorrenciaTest.corpoRegra(empresa, 20);
    if (contrato != null) corpo = corpo.replaceFirst("\\{", "{\"contratoId\":\"" + contrato + "\",");
    String r = mvc.perform(post("/api/financeiro/recorrencias").with(admin()).contentType(MediaType.APPLICATION_JSON).content(corpo))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String regra = JsonPath.read(r, "$.data.id");
    mvc.perform(post("/api/financeiro/recorrencias/" + regra + "/gerar-cobranca").with(admin())).andExpect(status().isCreated());
    return regra;
  }
}
