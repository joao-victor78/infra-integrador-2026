package br.com.erp.financeiro.saude;

import br.com.erp.financeiro.api.Resposta;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** RNF-11 / Contrato §8.5: 200 só se o banco responde; senão 503. Sem token. */
@Tag(name = "saude")
@RestController
public class SaudeController {

  private final JdbcTemplate jdbc;

  public SaudeController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/financeiro/health")
  public ResponseEntity<Resposta<Map<String, String>>> saude() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(Resposta.ok(Map.of("status", "UP")));
    } catch (DataAccessException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Resposta.falha("Banco de dados indisponível."));
    }
  }
}
