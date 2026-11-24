package br.com.erp.financeiro.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Quem está agindo: usuário do token, serviço ou rotina automática (jobs, consumidores, webhook). */
public final class Usuarios {

  private Usuarios() {}

  /** Token emitido para rotina sem usuário (Contrato §9.2). */
  public static boolean ehServico(Jwt jwt) {
    String sub = jwt.getSubject();
    return sub != null && sub.startsWith("svc:");
  }

  private static Optional<Jwt> jwt() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return a instanceof JwtAuthenticationToken t ? Optional.of(t.getToken()) : Optional.empty();
  }

  /** Id do usuário (created_by, updated_by, auditoria); nulo para serviço e rotina automática. */
  public static UUID atual() {
    return jwt().filter(j -> !ehServico(j)).map(j -> {
      try {
        return UUID.fromString(j.getSubject());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }).orElse(null);
  }

  /** "svc:contratos" quando a chamada vem de outro módulo com token de serviço (auditoria, §9.2). */
  public static String servicoAtual() {
    return jwt().filter(Usuarios::ehServico).map(Jwt::getSubject).orElse(null);
  }

  public static boolean tem(String permissao) {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null) return false;
    for (GrantedAuthority g : a.getAuthorities()) if (permissao.equals(g.getAuthority())) return true;
    return false;
  }

  /** IP de origem: primeiro X-Forwarded-For (posto pelo gateway) ou o endereço da conexão. */
  public static String ip(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return req.getRemoteAddr();
  }

  /** IP da requisição em andamento, quando há uma (jobs não têm). */
  public static String ipAtual() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s) return ip(s.getRequest());
    return null;
  }
}
