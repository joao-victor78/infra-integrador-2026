package br.com.erp.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.erp.financeiro.eventos.ConsumidorContratos;
import br.com.erp.financeiro.recorrencias.RecorrenciaJob;
import br.com.erp.financeiro.tenant.TenantContexto;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** RF-03: cobrança recorrente. */
class RecorrenciaTest extends TesteBase {

  static final String SERVICO_LICENCA = "11111111-1111-4111-8111-111111111111";
  static final String SERVICO_BACKUP = "22222222-2222-4222-8222-222222222222";

  @Autowired
  RecorrenciaJob job;

  @Autowired
  ConsumidorContratos consumidor;

  /** RF-03, critério 1: no dia configurado nasce uma cobrança com a soma dos itens, sem duplicar. */
  @Test
  void jobGeraNoDiaConfiguradoSemDuplicar() throws Exception {
    relogio.fixar(LocalDate.of(2026, 12, 1));
    UUID empresa = UUID.randomUUID();
    String regra = criarRegra(empresa, 20);

    relogio.fixar(LocalDate.of(2026, 12, 4));
    assertThat(TenantContexto.calcular(tenant, job::executarNoTenant)).isZero();

    relogio.fixar(LocalDate.of(2026, 12, 5));
    assertThat(TenantContexto.calcular(tenant, job::executarNoTenant)).isEqualTo(1);
    assertThat(TenantContexto.calcular(tenant, job::executarNoTenant)).isZero();

    mvc.perform(get("/api/financeiro/cobrancas?recorrenciaId=" + regra).with(admin()))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.itens[0].valor").value(1500.00)) // 20 × 45,00 + 500 × 1,20
        .andExpect(jsonPath("$.data.itens[0].competencia").value("2026-12-01"))
        .andExpect(jsonPath("$.data.itens[0].vencimento").value("2026-12-15"))
        .andExpect(jsonPath("$.data.itens[0].clienteId").value(empresa.toString()));
    assertThat(eventos("financeiro.cobranca.criada")).isEqualTo(1);

    // RN-15: gerar manualmente o mesmo ciclo é recusado
    mvc.perform(post("/api/financeiro/recorrencias/" + regra + "/gerar-cobranca").with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].codigo").value("CICLO_JA_GERADO"));
  }

  /** RF-03, critério 3 (RN-17): o aditivo vale para o próximo ciclo; as anteriores permanecem. */
  @Test
  void aditivoAlteraSoAsProximasGeracoes() throws Exception {
    relogio.fixar(LocalDate.of(2026, 12, 1));
    UUID empresa = UUID.randomUUID();
    String regra = criarRegra(empresa, 20);
    mvc.perform(post("/api/financeiro/recorrencias/" + regra + "/gerar-cobranca").with(admin()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.valor").value(1500.00));

    mvc.perform(put("/api/financeiro/recorrencias/" + regra).with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(corpoRegra(empresa, 25)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.proximaCompetencia").value("2027-01-01"))
        .andExpect(jsonPath("$.data.valorProximoCiclo").value(1725.00));

    relogio.fixar(LocalDate.of(2027, 1, 5));
    assertThat(TenantContexto.calcular(tenant, job::executarNoTenant)).isEqualTo(1);
    String lista = mvc.perform(get("/api/financeiro/cobrancas?recorrenciaId=" + regra + "&ordenar=vencimento,asc").with(admin()))
        .andReturn().getResponse().getContentAsString();
    JsonNode itens = ler(lista).at("/data/itens");
    assertThat(itens.size()).isEqualTo(2);
    assertThat(itens.get(0).path("valor").decimalValue()).isEqualByComparingTo("1500.00");
    assertThat(itens.get(1).path("valor").decimalValue()).isEqualByComparingTo("1725.00");
  }

  /** RF-03, critério 2: o mesmo evento de contrato assinado duas vezes cria uma regra só. */
  @Test
  void contratoAssinadoDuasVezesCriaUmaRegra() throws Exception {
    UUID contrato = UUID.randomUUID();
    String envelope = """
        {"id":"%s","tipo":"contratos.contrato.assinado","versao":1,"tenantId":"%s","moduloOrigem":"contratos",
         "ocorridoEm":"2026-11-20T13:02:11Z","usuarioId":null,"correlacaoId":null,
         "dados":{"contratoId":"%s","empresaId":"%s","diaGeracao":10,
                  "itens":[{"servicoId":"%s","descricao":"Licença","quantidade":10,"unidade":"UN","valorUnitario":50.00}]}}"""
        .formatted(UUID.randomUUID(), tenant, contrato, UUID.randomUUID(), SERVICO_LICENCA);
    var mensagem = MessageBuilder.withBody(envelope.getBytes(StandardCharsets.UTF_8)).build();
    consumidor.contratoAssinado(mensagem);
    consumidor.contratoAssinado(mensagem);
    mvc.perform(get("/api/financeiro/recorrencias").with(admin()))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.itens[0].contratoId").value(contrato.toString()))
        .andExpect(jsonPath("$.data.itens[0].valorProximoCiclo").value(500.00));
  }

  @Test
  void itemSemServicoResponde400() throws Exception {
    mvc.perform(post("/api/financeiro/recorrencias").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"clienteId":"%s","diaGeracao":5,"itens":[{"descricao":"X","quantidade":1,"unidade":"UN","valorUnitario":10.00}]}"""
                .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].campo").value("itens[0].servicoId"));
    mvc.perform(post("/api/financeiro/recorrencias").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\":\"%s\",\"diaGeracao\":29,\"itens\":[]}".formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());
  }

  String criarRegra(UUID empresa, int licencas) throws Exception {
    String r = mvc.perform(post("/api/financeiro/recorrencias").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(corpoRegra(empresa, licencas)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(r, "$.data.id");
  }

  static String corpoRegra(UUID empresa, int licencas) {
    return """
        {"clienteId":"%s","descricao":"Plano gerenciado","periodicidade":"MENSAL","diaGeracao":5,"diasParaVencimento":10,
         "formaPagamento":"PIX","itens":[
           {"servicoId":"%s","descricao":"Licenças","quantidade":%d,"unidade":"UN","valorUnitario":45.00},
           {"servicoId":"%s","descricao":"Backup em nuvem","quantidade":500,"unidade":"GB","valorUnitario":1.20,"franquia":400}]}"""
        .formatted(empresa, SERVICO_LICENCA, licencas, SERVICO_BACKUP);
  }
}
