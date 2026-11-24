package br.com.erp.financeiro.auditoria;

import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.seguranca.Usuarios;
import br.com.erp.financeiro.tenant.TenantContexto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de quem alterou o quê (RF-13, RN-64 a RN-67). Grava na mesma transação da operação:
 * se a operação desfaz, a auditoria desfaz junto. A tabela só aceita INSERT (gatilho + REVOKE).
 */
@Service
public class AuditoriaService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Relogio relogio;

  public AuditoriaService(JdbcTemplate jdbc, ObjectMapper mapper, Relogio relogio) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.relogio = relogio;
  }

  public record Registro(UUID id, UUID usuarioId, String servico, String ip, OffsetDateTime dataHora,
      String acao, String entidade, UUID registroId, JsonNode valorAnterior, JsonNode valorNovo) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrar(String acao, String entidade, UUID registroId, Object anterior, Object novo) {
    jdbc.update("""
        INSERT INTO financeiro.auditoria
          (id, tenant_id, usuario_id, servico, ip, data_hora, acao, entidade, registro_id, valor_anterior, valor_novo)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))""",
        UUID.randomUUID(), TenantContexto.exigir(), Usuarios.atual(), Usuarios.servicoAtual(), Usuarios.ipAtual(),
        Timestamp.from(relogio.agora()), acao, entidade, registroId, json(anterior), json(novo));
  }

  private String json(Object valor) {
    if (valor == null) return null;
    try {
      return mapper.writeValueAsString(valor);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Não foi possível serializar o registro de auditoria", e);
    }
  }

  @Transactional(readOnly = true)
  public Pagina<Registro> listar(String entidade, UUID registroId, int pagina, int tamanho) {
    StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
    List<Object> args = new ArrayList<>(List.of(TenantContexto.exigir()));
    if (entidade != null && !entidade.isBlank()) {
      where.append(" AND entidade = ?");
      args.add(entidade);
    }
    if (registroId != null) {
      where.append(" AND registro_id = ?");
      args.add(registroId);
    }
    Long total = jdbc.queryForObject("SELECT count(*) FROM financeiro.auditoria" + where, Long.class, args.toArray());
    List<Object> argsPagina = new ArrayList<>(args);
    argsPagina.add(tamanho);
    argsPagina.add((long) pagina * tamanho);
    List<Registro> itens = jdbc.query("SELECT * FROM financeiro.auditoria" + where
        + " ORDER BY data_hora DESC, id LIMIT ? OFFSET ?", (rs, i) -> new Registro(
            rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getString("servico"),
            rs.getString("ip"), rs.getTimestamp("data_hora").toInstant().atOffset(ZoneOffset.UTC),
            rs.getString("acao"), rs.getString("entidade"), rs.getObject("registro_id", UUID.class),
            ler(rs.getString("valor_anterior")), ler(rs.getString("valor_novo"))),
        argsPagina.toArray());
    return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
  }

  private JsonNode ler(String json) {
    if (json == null) return null;
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
