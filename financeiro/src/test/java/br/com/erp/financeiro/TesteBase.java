package br.com.erp.financeiro;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.plataforma.ContatosDaEmpresa;
import br.com.erp.financeiro.webhooks.LimitePorIpFiltro;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base dos testes de integração: a aplicação inteira com PostgreSQL real, sem nenhum outro módulo no ar
 * (Contrato §14.5). RabbitMQ e CRM são mocks; os eventos são conferidos na tabela eventos_outbox.
 *
 * <p>Banco: TEST_DB_URL (padrão jdbc:postgresql://localhost:5432/erp_test, usuário financeiro). O schema
 * financeiro desse banco é apagado e recriado; por segurança, só em banco cujo nome termina em "_test".
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class TesteBase {

  static final String URL = env("TEST_DB_URL", "jdbc:postgresql://localhost:5432/erp_test");
  static final String USUARIO = env("TEST_DB_USER", "financeiro");
  static final String SENHA = env("TEST_DB_PASSWORD", "financeiro");

  public static final String VER = "financeiro.contareceber.ver";
  public static final String CRIAR = "financeiro.contareceber.criar";
  public static final String EDITAR = "financeiro.contareceber.editar";
  public static final String EXCLUIR = "financeiro.contareceber.excluir";

  static {
    if (!URL.replaceAll("\\?.*$", "").endsWith("_test")) {
      throw new IllegalStateException("Os testes apagam o schema financeiro: use um banco terminado em _test (" + URL + ")");
    }
    try (Connection c = DriverManager.getConnection(URL, USUARIO, SENHA)) {
      c.createStatement().execute("DROP SCHEMA IF EXISTS financeiro CASCADE");
    } catch (Exception e) {
      throw new IllegalStateException("Banco de testes indisponível em " + URL + ": " + e.getMessage(), e);
    }
  }

  @DynamicPropertySource
  static void banco(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> URL);
    r.add("spring.datasource.username", () -> USUARIO);
    r.add("spring.datasource.password", () -> SENHA);
    r.add("spring.flyway.url", () -> URL);
    r.add("spring.flyway.user", () -> USUARIO);
    r.add("spring.flyway.password", () -> SENHA);
    r.add("spring.flyway.create-schemas", () -> "true");
    r.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    r.add("financeiro.jobs.ativos", () -> "false");
    r.add("financeiro.webhook.segredo", () -> "segredo-de-teste");
    r.add("financeiro.webhook.limite-por-minuto", () -> "1000");
    r.add("financeiro.jwt.chave-publica-arquivo", () -> "");
    r.add("logging.structured.format.console", () -> "");
    r.add("ENV_FILE", () -> "nao-existe.env");
  }

  @MockitoBean
  protected RabbitTemplate rabbit;

  @MockitoBean
  protected ContatosDaEmpresa contatos;

  @Autowired
  protected MockMvc mvc;

  @Autowired
  protected JdbcTemplate jdbc;

  @Autowired
  protected Relogio relogio;

  @Autowired
  protected ObjectMapper mapper;

  @Autowired
  protected LimitePorIpFiltro limite;

  protected final UUID tenant = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    Mockito.when(contatos.email(Mockito.any(), Mockito.any())).thenReturn(Optional.of("financeiro@cliente.com.br"));
    limite.zerar();
  }

  @AfterEach
  void restaurarRelogio() {
    relogio.fixar(null);
  }

  static String env(String nome, String padrao) {
    String v = System.getenv(nome);
    return v == null || v.isBlank() ? padrao : v;
  }

  /** Token de usuário do tenant, com as permissões informadas (claim perms). */
  protected static RequestPostProcessor usuario(UUID tenant, String... permissoes) {
    return usuarioComId(tenant, UUID.randomUUID(), permissoes);
  }

  protected static RequestPostProcessor usuarioComId(UUID tenant, UUID sub, String... permissoes) {
    return jwt().jwt(t -> t.subject(sub.toString()).claim("tenant_id", tenant.toString()).claim("perms", List.of(permissoes)))
        .authorities(Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
  }

  /** Token de serviço: sub = svc:{modulo}, sem tenant_id (Contrato §9.2). */
  protected static RequestPostProcessor servico(String modulo, String... permissoes) {
    return jwt().jwt(t -> t.subject("svc:" + modulo).claim("perms", List.of(permissoes)))
        .authorities(Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
  }

  protected RequestPostProcessor admin() {
    return usuario(tenant, "financeiro.acessar", "financeiro.cobranca.ver_resumo", VER, CRIAR, EDITAR, EXCLUIR,
        "financeiro.contapagar.ver", "financeiro.contapagar.criar", "financeiro.contapagar.aprovar",
        "financeiro.recorrencia.ver", "financeiro.recorrencia.administrar", "financeiro.regua.ver",
        "financeiro.regua.administrar", "financeiro.inadimplencia.ver", "financeiro.dashboard.ver",
        "financeiro.relatorio.exportar", "financeiro.auditoria.ver");
  }

  protected LocalDate hoje() {
    return relogio.hoje();
  }

  protected String json(Object o) throws Exception {
    return mapper.writeValueAsString(o);
  }

  protected JsonNode ler(String corpo) throws Exception {
    return mapper.readTree(corpo);
  }

  /** Lança uma cobrança e devolve o id. */
  protected String criarCobranca(UUID empresa, String valor, LocalDate vencimento) throws Exception {
    String corpo = """
        {"clienteId":"%s","competencia":"%s","vencimento":"%s","valor":%s,"descricao":"Mensalidade de teste"}"""
        .formatted(empresa, vencimento.withDayOfMonth(1), vencimento, valor);
    String r = mvc.perform(post("/api/financeiro/cobrancas").with(admin()).contentType(MediaType.APPLICATION_JSON).content(corpo))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(r, "$.data.id");
  }

  protected int eventos(String routingKey) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM financeiro.eventos_outbox WHERE tenant_id = ? AND routing_key = ?",
        Integer.class, tenant, routingKey);
    return n == null ? 0 : n;
  }
}
