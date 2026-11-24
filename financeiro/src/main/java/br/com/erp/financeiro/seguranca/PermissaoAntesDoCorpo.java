package br.com.erp.financeiro.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O @PreAuthorize só roda depois que o Spring lê e valida o corpo; sem isto, quem não tem permissão
 * e manda um corpo inválido receberia 400 em vez de 403 (RNF-05). Este interceptor confere a mesma
 * permissão antes de qualquer leitura. O @PreAuthorize continua no método como segunda barreira.
 */
@Configuration
public class PermissaoAntesDoCorpo implements HandlerInterceptor, WebMvcConfigurer {

  private static final Pattern PERMISSAO = Pattern.compile("hasAuthority\\('([^']+)'\\)");

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/api/financeiro/**");
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    if (!(handler instanceof HandlerMethod metodo)) return true;
    PreAuthorize regra = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
    if (regra == null) return true;
    for (String permissao : exigidas(regra.value())) {
      if (!Usuarios.tem(permissao)) throw new AccessDeniedException("Sem a permissão " + permissao);
    }
    return true;
  }

  static List<String> exigidas(String expressao) {
    Matcher m = PERMISSAO.matcher(expressao);
    java.util.ArrayList<String> lista = new java.util.ArrayList<>();
    while (m.find()) lista.add(m.group(1));
    return lista;
  }
}
