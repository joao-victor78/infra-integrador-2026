package br.com.erp.financeiro.seguranca;

import br.com.erp.financeiro.tenant.TenantContexto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Define o tenant da requisição a partir do token (Contrato §6, RNF-01).
 * Token de usuário: claim tenant_id. Token de serviço (sub = "svc:..."): cabeçalho X-Tenant-Id,
 * lido só nesse caso (§9.2). Um tenantId no corpo ou na query string nunca é usado.
 */
public class TenantFiltro extends OncePerRequestFilter {

  public static final String CABECALHO_TENANT = "X-Tenant-Id";

  private final EscritorDeErro erros;

  public TenantFiltro(EscritorDeErro erros) {
    this.erros = erros;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
      throws ServletException, IOException {
    Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
    if (!(autenticacao instanceof JwtAuthenticationToken token)) {
      cadeia.doFilter(req, res); // rota pública ou requisição sem token
      return;
    }
    Jwt jwt = token.getToken();
    boolean servico = Usuarios.ehServico(jwt);
    String valor = servico ? req.getHeader(CABECALHO_TENANT) : jwt.getClaimAsString("tenant_id");
    UUID tenant;
    try {
      tenant = UUID.fromString(valor);
    } catch (IllegalArgumentException | NullPointerException e) {
      if (servico) erros.escrever(res, 400, "Token de serviço exige o cabeçalho X-Tenant-Id com um UUID válido.");
      else erros.escrever(res, 401, "Token sem tenant_id válido.");
      return;
    }
    try {
      TenantContexto.definir(tenant);
      cadeia.doFilter(req, res);
    } finally {
      TenantContexto.limpar();
    }
  }
}
