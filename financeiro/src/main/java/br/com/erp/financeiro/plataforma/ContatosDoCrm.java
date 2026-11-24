package br.com.erp.financeiro.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import br.com.erp.financeiro.observabilidade.CorrelacaoFiltro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Busca no CRM o e-mail de contato da empresa, com token de serviço e X-Tenant-Id (Contrato §9.2), pois
 * a régua roda sem usuário. Timeout de 3 s (RNF-09): falha não derruba o job, só deixa o disparo sem
 * destinatário de e-mail (a linha na timeline sai do mesmo jeito).
 */
@Component
public class ContatosDoCrm implements ContatosDaEmpresa {

  private static final Logger log = LoggerFactory.getLogger(ContatosDoCrm.class);

  private final HttpClient http;
  private final ObjectMapper mapper;
  private final String identityUrl;
  private final String crmUrl;
  private final String clientId;
  private final String clientSecret;
  private final Duration timeout;
  private volatile String token;
  private volatile Instant tokenExpiraEm = Instant.EPOCH;

  public ContatosDoCrm(ObjectMapper mapper,
      @Value("${financeiro.integracoes.identity-url}") String identityUrl,
      @Value("${financeiro.integracoes.crm-url}") String crmUrl,
      @Value("${financeiro.integracoes.client-id}") String clientId,
      @Value("${financeiro.integracoes.client-secret:}") String clientSecret,
      @Value("${financeiro.integracoes.timeout-ms:3000}") long timeoutMs) {
    this.mapper = mapper;
    this.identityUrl = identityUrl;
    this.crmUrl = crmUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.timeout = Duration.ofMillis(timeoutMs);
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public Optional<String> email(UUID tenant, UUID empresaId) {
    if (clientSecret == null || clientSecret.isBlank()) return Optional.empty(); // sem credencial de serviço
    try {
      HttpRequest req = HttpRequest.newBuilder(URI.create(crmUrl + "/api/crm/empresas/" + empresaId + "/contatos"))
          .timeout(timeout)
          .header("Authorization", "Bearer " + tokenDeServico())
          .header("X-Tenant-Id", tenant.toString())
          .header(CorrelacaoFiltro.CABECALHO, Optional.ofNullable(MDC.get(CorrelacaoFiltro.CHAVE_MDC))
              .orElseGet(() -> UUID.randomUUID().toString()))
          .GET().build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (res.statusCode() != 200) return Optional.empty();
      for (JsonNode contato : mapper.readTree(res.body()).path("data")) {
        String email = contato.path("email").asText("");
        if (!email.isBlank()) return Optional.of(email);
      }
    } catch (Exception e) {
      log.warn("Contatos da empresa {} indisponíveis no CRM: {}", empresaId, e.getMessage());
    }
    return Optional.empty();
  }

  private String tokenDeServico() throws Exception {
    if (token != null && Instant.now().isBefore(tokenExpiraEm)) return token;
    String corpo = mapper.writeValueAsString(Map.of("clientId", clientId, "clientSecret", clientSecret));
    HttpRequest req = HttpRequest.newBuilder(URI.create(identityUrl + "/api/identity/auth/token-servico"))
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (res.statusCode() != 200) throw new IllegalStateException("identity respondeu " + res.statusCode());
    JsonNode raiz = mapper.readTree(res.body());
    JsonNode dados = raiz.has("data") ? raiz.path("data") : raiz;
    token = dados.path("accessToken").asText();
    long expira = dados.path("expiresIn").asLong(900);
    tokenExpiraEm = Instant.now().plusSeconds(Math.max(60, expira - 60));
    return token;
  }
}
