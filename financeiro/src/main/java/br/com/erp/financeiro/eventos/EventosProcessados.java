package br.com.erp.financeiro.eventos;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ids de eventos já tratados (Contrato §9.7). Roda na mesma transação do efeito: os dois vão juntos. */
@Repository
public class EventosProcessados {

  private final JdbcTemplate jdbc;

  public EventosProcessados(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** @return true na primeira vez que o id aparece; false se já foi processado */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean registrar(UUID eventoId, String tipo, String origem) {
    return jdbc.update("""
        INSERT INTO financeiro.eventos_processados (evento_id, tipo, origem) VALUES (?, ?, ?)
        ON CONFLICT (evento_id) DO NOTHING""", eventoId, tipo, origem) == 1;
  }
}
