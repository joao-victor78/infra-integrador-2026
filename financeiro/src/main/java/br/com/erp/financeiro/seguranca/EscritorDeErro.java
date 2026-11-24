package br.com.erp.financeiro.seguranca;

import br.com.erp.financeiro.api.Resposta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Escreve erros dos filtros (401, 403, 429...) no mesmo envelope das outras respostas. */
public class EscritorDeErro {

  private final ObjectMapper mapper;

  public EscritorDeErro(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void escrever(HttpServletResponse resposta, int status, String mensagem) throws IOException {
    resposta.setStatus(status);
    resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
    resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
    mapper.writeValue(resposta.getOutputStream(), Resposta.falha(mensagem));
  }
}
