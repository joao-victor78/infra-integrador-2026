package br.com.erp.financeiro.regua;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.ErroCampo;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.eventos.Outbox;
import br.com.erp.financeiro.eventos.PedidosPlataforma;
import br.com.erp.financeiro.eventos.TiposDeEvento;
import br.com.erp.financeiro.inadimplencia.PromessaPagamento;
import br.com.erp.financeiro.inadimplencia.PromessaRepository;
import br.com.erp.financeiro.plataforma.ContatosDaEmpresa;
import br.com.erp.financeiro.regua.ReguaDtos.Disparo;
import br.com.erp.financeiro.regua.ReguaDtos.Etapa;
import br.com.erp.financeiro.regua.ReguaDtos.Execucao;
import br.com.erp.financeiro.tenant.TenantContexto;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Régua de cobrança (RF-06) e o job diário que marca atrasos (RN-06) e dispara avisos — diagrama de
 * atividades "Job diário de atraso e régua de cobrança". Rodar duas vezes no mesmo dia não repete nada:
 * o evento de atraso sai uma vez por cobrança e cada (cobrança, etapa) tem um disparo só (RN-32, RNF-03).
 */
@Service
public class ReguaService {

  static final String ENTIDADE = "regua";
  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String ROTA = "/modulos/financeiro/cobrancas/";

  private final ReguaEtapaRepository etapas;
  private final CobrancaRepository cobrancas;
  private final PromessaRepository promessas;
  private final AuditoriaService auditoria;
  private final Outbox outbox;
  private final PedidosPlataforma plataforma;
  private final ContatosDaEmpresa contatos;
  private final JdbcTemplate jdbc;
  private final Relogio relogio;

  public ReguaService(ReguaEtapaRepository etapas, CobrancaRepository cobrancas, PromessaRepository promessas,
      AuditoriaService auditoria, Outbox outbox, PedidosPlataforma plataforma, ContatosDaEmpresa contatos,
      JdbcTemplate jdbc, Relogio relogio) {
    this.etapas = etapas;
    this.cobrancas = cobrancas;
    this.promessas = promessas;
    this.auditoria = auditoria;
    this.outbox = outbox;
    this.plataforma = plataforma;
    this.contatos = contatos;
    this.jdbc = jdbc;
    this.relogio = relogio;
  }

  @Transactional(readOnly = true)
  public List<Etapa> ver() {
    return etapas.configuradas().stream().map(Etapa::de).toList();
  }

  /** Substitui a configuração inteira (PUT). Etapa com id é mantida (preserva o histórico de disparos). */
  @Transactional
  public List<Etapa> atualizar(List<Etapa> novas) {
    Set<Integer> vistos = new HashSet<>();
    List<ErroCampo> erros = new ArrayList<>();
    for (int i = 0; i < novas.size(); i++) {
      if (!vistos.add(novas.get(i).diasOffset())) {
        erros.add(new ErroCampo("etapas[" + i + "].diasOffset", "DESLOCAMENTO_DUPLICADO",
            "Já existe outra etapa com deslocamento " + novas.get(i).diasOffset() + "."));
      }
    }
    if (!erros.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Dados inválidos: confira os campos.", erros);

    List<ReguaEtapa> atuais = etapas.configuradas();
    List<Etapa> antes = atuais.stream().map(Etapa::de).toList();
    Map<UUID, ReguaEtapa> porId = new HashMap<>();
    atuais.forEach(e -> porId.put(e.getId(), e));
    Set<UUID> mantidas = new HashSet<>();
    novas.stream().map(Etapa::id).filter(porId::containsKey).forEach(mantidas::add);

    // 1) remove as que saíram; 2) tira as mantidas do caminho do índice único; 3) aplica os valores finais.
    atuais.stream().filter(e -> !mantidas.contains(e.getId())).forEach(ReguaEtapa::excluir);
    int provisorio = 30_000;
    for (ReguaEtapa e : atuais) if (mantidas.contains(e.getId())) e.deslocamentoProvisorio(provisorio++);
    etapas.flush();
    for (Etapa n : novas) {
      boolean ativa = n.ativa() == null || n.ativa();
      String acao = n.acao() == null || n.acao().isBlank() ? null : n.acao().trim();
      ReguaEtapa existente = n.id() == null ? null : porId.get(n.id());
      if (existente != null) existente.configurar(n.diasOffset(), acao, n.canal(), n.templateMensagem().trim(), ativa);
      else etapas.save(new ReguaEtapa(n.diasOffset(), acao, n.canal(), n.templateMensagem().trim(), ativa));
    }
    etapas.flush();
    List<Etapa> depois = ver();
    auditoria.registrar("ALTERAR_REGUA", ENTIDADE, TenantContexto.exigir(), Map.of("etapas", antes), Map.of("etapas", depois));
    return depois;
  }

  @Transactional(readOnly = true)
  public List<Disparo> disparosDaCobranca(UUID cobrancaId) {
    return jdbc.query("""
        SELECT d.id, d.cobranca_id, d.etapa_id, e.deslocamento_dias, d.canal, d.situacao, d.disparado_em
          FROM financeiro.regua_disparos d JOIN financeiro.regua_etapas e ON e.id = d.etapa_id
         WHERE d.tenant_id = ? AND d.cobranca_id = ? ORDER BY d.disparado_em""",
        (rs, i) -> new Disparo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
            rs.getInt(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant().atOffset(ZoneOffset.UTC)),
        TenantContexto.exigir(), cobrancaId);
  }

  /** Uma execução do job para o tenant do contexto, numa transação (o commit libera os eventos). */
  @Transactional
  public Execucao executar() {
    LocalDate hoje = relogio.hoje();
    int[] atraso = marcarAtrasos(hoje);
    int disparos = dispararEtapas(hoje);
    int quebradas = 0;
    for (PromessaPagamento p : promessas.vencidas(hoje)) {
      p.quebrar();
      quebradas++;
    }
    return new Execucao(atraso[0], atraso[1], disparos, quebradas);
  }

  /** RN-06: ABERTO ou PARCIAL vencida vira ATRASADO; financeiro.pagamento.atrasado uma vez por cobrança. */
  private int[] marcarAtrasos(LocalDate hoje) {
    int marcadas = 0;
    int eventos = 0;
    for (Cobranca c : cobrancas.vencidasEm(
        List.of(StatusCobranca.ABERTO, StatusCobranca.PARCIAL, StatusCobranca.ATRASADO), hoje)) {
      if (c.getStatus() != StatusCobranca.ATRASADO) {
        Map<String, Object> antes = c.instantaneo();
        c.marcarAtrasada();
        auditoria.registrar("MARCAR_ATRASO", CobrancaService.ENTIDADE, c.getId(), antes, c.instantaneo());
        marcadas++;
      }
      if (c.getAtrasoPublicadoEm() == null) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("cobrancaId", c.getId());
        dados.put("clienteId", c.getEmpresaId());
        dados.put("empresaId", c.getEmpresaId());
        dados.put("vencimento", c.getDataVencimento());
        dados.put("diasAtraso", c.diasAtraso(hoje));
        dados.put("saldoDevedor", c.saldo());
        outbox.gravar(TiposDeEvento.PAGAMENTO_ATRASADO, dados);
        c.marcarAtrasoPublicado(relogio.agoraUtc());
        eventos++;
      }
    }
    return new int[] {marcadas, eventos};
  }

  /** RF-06: para cada etapa ativa, as cobranças em aberto cujo vencimento + deslocamento = hoje. */
  private int dispararEtapas(LocalDate hoje) {
    int total = 0;
    for (ReguaEtapa etapa : etapas.ativas()) {
      LocalDate vencimento = hoje.minusDays(etapa.getDeslocamentoDias());
      for (Cobranca c : cobrancas.vencendoEm(StatusCobranca.EM_ABERTO, vencimento)) {
        Integer ja = jdbc.queryForObject("SELECT count(*) FROM financeiro.regua_disparos WHERE cobranca_id = ? AND etapa_id = ?",
            Integer.class, c.getId(), etapa.getId());
        if (ja != null && ja > 0) continue; // job rodou duas vezes no mesmo dia (RNF-03)
        if (disparar(etapa, c, hoje)) total++;
      }
    }
    return total;
  }

  private boolean disparar(ReguaEtapa etapa, Cobranca c, LocalDate hoje) {
    String mensagem = montarMensagem(etapa.getModeloMensagem(), c, hoje);
    String situacao;
    switch (etapa.getCanal()) {
      case EMAIL -> situacao = contatos.email(TenantContexto.exigir(), c.getEmpresaId()).map(para -> {
        Map<String, String> variaveis = new LinkedHashMap<>();
        variaveis.put("mensagem", mensagem);
        variaveis.put("valor", Dinheiro.brl(c.saldo()));
        variaveis.put("vencimento", c.getDataVencimento().format(DATA));
        variaveis.put("etapa", etapa.rotulo());
        plataforma.email(para, "financeiro.cobranca-regua", variaveis); // emails/financeiro/cobranca-regua.txt do infra
        return "SOLICITADO";
      }).orElse("SEM_DESTINATARIO");
      case NOTIFICACAO_INTERNA -> {
        if (c.getCreatedBy() != null) {
          plataforma.notificacao(c.getCreatedBy(), "COBRANCA_VENCIDA", "Régua " + etapa.rotulo() + ": cobrança "
              + c.getId().toString().substring(0, 8), mensagem, ROTA + c.getId());
          situacao = "SOLICITADO";
        } else {
          situacao = "SEM_DESTINATARIO";
        }
      }
      default -> situacao = "CANAL_INDISPONIVEL"; // WhatsApp sem dono nesta fase (RN-37)
    }
    Timestamp agora = Timestamp.from(relogio.agora());
    int inseridos = jdbc.update("""
        INSERT INTO financeiro.regua_disparos
          (id, tenant_id, cobranca_id, etapa_id, disparado_em, canal, situacao, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (cobranca_id, etapa_id) DO NOTHING""",
        UUID.randomUUID(), TenantContexto.exigir(), c.getId(), etapa.getId(), agora, etapa.getCanal().name(), situacao,
        agora, agora);
    if (inseridos == 0) return false;
    plataforma.timeline(c.getEmpresaId(), "financeiro.regua.disparo",
        "Régua " + etapa.rotulo() + " (" + etapa.getCanal() + "): " + mensagem, ROTA + c.getId());
    return true;
  }

  /** Variáveis do modelo: {valor}, {saldo}, {vencimento}, {dias}, {descricao}, {cobranca}. */
  static String montarMensagem(String modelo, Cobranca c, LocalDate hoje) {
    return modelo
        .replace("{valor}", Dinheiro.brl(c.totalDevido()))
        .replace("{saldo}", Dinheiro.brl(c.saldo()))
        .replace("{vencimento}", c.getDataVencimento().format(DATA))
        .replace("{dias}", String.valueOf(c.diasAtraso(hoje)))
        .replace("{descricao}", c.getDescricao() == null ? "" : c.getDescricao())
        .replace("{cobranca}", c.getId().toString().substring(0, 8));
  }
}
