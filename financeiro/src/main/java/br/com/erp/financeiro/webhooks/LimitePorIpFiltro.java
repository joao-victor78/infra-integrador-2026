package br.com.erp.financeiro.webhooks;

import br.com.erp.financeiro.seguranca.EscritorDeErro;
import br.com.erp.financeiro.seguranca.Usuarios;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de requisições por IP em toda rota pública (RN-25, Contrato §12.6): janela fixa de um minuto.
 * Acima do limite responde 429 no envelope, antes de ler o corpo.
 */
@Component
public class LimitePorIpFiltro extends OncePerRequestFilter {

  private final int limitePorMinuto;
  private final EscritorDeErro erros;
  private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

  private record Janela(long minuto, AtomicInteger contagem) {}

  public LimitePorIpFiltro(@Value("${financeiro.webhook.limite-por-minuto:60}") int limitePorMinuto, ObjectMapper mapper) {
    this.limitePorMinuto = limitePorMinuto;
    this.erros = new EscritorDeErro(mapper);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return !req.getRequestURI().startsWith("/public/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
      throws ServletException, IOException {
    long minuto = System.currentTimeMillis() / 60_000;
    String ip = Usuarios.ip(req);
    Janela j = janelas.compute(ip, (k, atual) -> atual == null || atual.minuto() != minuto
        ? new Janela(minuto, new AtomicInteger()) : atual);
    if (j.contagem().incrementAndGet() > limitePorMinuto) {
      res.setHeader("Retry-After", "60");
      erros.escrever(res, 429, "Muitas requisições deste endereço. Tente de novo em um minuto.");
      return;
    }
    if (janelas.size() > 10_000) janelas.entrySet().removeIf(e -> e.getValue().minuto() != minuto);
    cadeia.doFilter(req, res);
  }

  /** Para testes. */
  public void zerar() {
    janelas.clear();
  }
}
