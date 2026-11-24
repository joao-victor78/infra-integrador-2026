package br.com.erp.financeiro.observabilidade;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Coloca o X-Request-Id do gateway em todo log da requisição e no correlacaoId dos eventos (RNF-10). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacaoFiltro extends OncePerRequestFilter {

  public static final String CABECALHO = "X-Request-Id";
  public static final String CHAVE_MDC = "requestId";

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
      throws ServletException, IOException {
    String id = Optional.ofNullable(req.getHeader(CABECALHO))
        .filter(v -> !v.isBlank() && v.length() <= 100)
        .orElseGet(() -> UUID.randomUUID().toString());
    MDC.put(CHAVE_MDC, id);
    res.setHeader(CABECALHO, id);
    try {
      cadeia.doFilter(req, res);
    } finally {
      MDC.remove(CHAVE_MDC);
    }
  }
}
