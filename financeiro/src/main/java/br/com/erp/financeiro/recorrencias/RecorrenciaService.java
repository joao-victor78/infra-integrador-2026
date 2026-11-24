package br.com.erp.financeiro.recorrencias;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.comum.Competencia;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.comum.Tenant;
import br.com.erp.financeiro.recorrencias.RecorrenciaDtos.ItemEntrada;
import br.com.erp.financeiro.recorrencias.RecorrenciaDtos.RegraEntrada;
import br.com.erp.financeiro.recorrencias.RecorrenciaDtos.RegraSaida;
import br.com.erp.financeiro.tenant.TenantContexto;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobrança recorrente (RF-03). O valor do ciclo é sempre a soma dos itens vigentes (RN-14); cada regra
 * gera no máximo uma cobrança por competência (RN-15, UNIQUE em recorrencia_geracoes); um aditivo só
 * vale das gerações seguintes em diante (RN-17).
 */
@Service
public class RecorrenciaService {

  static final String ENTIDADE = "recorrencia";

  public enum Origem { JOB, MANUAL }

  private final RecorrenciaRepository regras;
  private final RecorrenciaItemRepository itens;
  private final CobrancaRepository cobrancas;
  private final CobrancaService cobrancaService;
  private final AuditoriaService auditoria;
  private final JdbcTemplate jdbc;
  private final Relogio relogio;

  public RecorrenciaService(RecorrenciaRepository regras, RecorrenciaItemRepository itens, CobrancaRepository cobrancas,
      CobrancaService cobrancaService, AuditoriaService auditoria, JdbcTemplate jdbc, Relogio relogio) {
    this.regras = regras;
    this.itens = itens;
    this.cobrancas = cobrancas;
    this.cobrancaService = cobrancaService;
    this.auditoria = auditoria;
    this.jdbc = jdbc;
    this.relogio = relogio;
  }

  @Transactional(readOnly = true)
  public Pagina<RegraSaida> listar(UUID clienteId, Boolean ativa, Pageable pagina) {
    Specification<Recorrencia> s = (r, q, cb) -> cb.isNull(r.get("deletedAt"));
    if (clienteId != null) s = s.and((r, q, cb) -> cb.equal(r.get("empresaId"), clienteId));
    if (ativa != null) s = s.and((r, q, cb) -> cb.equal(r.get("ativa"), ativa));
    Page<Recorrencia> p = regras.findAll(s, pagina);
    Map<UUID, List<RecorrenciaItem>> porRegra = p.isEmpty() ? Map.of()
        : itens.dasRegras(p.map(Recorrencia::getId).toList()).stream().collect(Collectors.groupingBy(RecorrenciaItem::getRecorrenciaId));
    return Pagina.de(p, r -> RegraSaida.de(r, porRegra.getOrDefault(r.getId(), List.of())));
  }

  @Transactional(readOnly = true)
  public RegraSaida ver(UUID id) {
    Recorrencia r = obter(id);
    return RegraSaida.de(r, itens.daRegra(r.getId()));
  }

  Recorrencia obter(UUID id) {
    return Tenant.ativoDoTenant(regras.findById(id), "Regra de recorrência");
  }

  /** A primeira competência é a do mês atual: o job gera quando chega o dia de geração. */
  @Transactional
  public RegraSaida criar(RegraEntrada e) {
    String inicio = Competencia.de(relogio.hoje());
    Recorrencia r = new Recorrencia(e.clienteId(), e.contratoId(), inicio, relogio.hoje());
    configurar(r, e);
    if (Boolean.FALSE.equals(e.ativa())) r.encerrar(relogio.hoje());
    regras.save(r);
    List<RecorrenciaItem> novos = salvarItens(r.getId(), e.itens(), inicio);
    auditoria.registrar("CRIAR", ENTIDADE, r.getId(), null, Map.of("regra", r.instantaneo(), "itens", e.itens()));
    return RegraSaida.de(r, novos);
  }

  /**
   * RN-17: os itens novos valem da próxima competência a gerar; os vigentes até ali são encerrados na
   * competência anterior. Cobranças já emitidas não mudam (guardam o próprio valor).
   */
  @Transactional
  public RegraSaida atualizar(UUID id, RegraEntrada e) {
    Recorrencia r = obter(id);
    List<RecorrenciaItem> atuais = itens.daRegra(r.getId());
    Map<String, Object> antes = Map.of("regra", r.instantaneo(), "itens", atuais.stream()
        .filter(i -> i.vigenteEm(r.getProximaCompetencia())).map(RecorrenciaDtos.ItemSaida::de).toList());
    configurar(r, e);
    if (Boolean.FALSE.equals(e.ativa()) && r.isAtiva()) r.encerrar(relogio.hoje());
    else if (Boolean.TRUE.equals(e.ativa()) && !r.isAtiva()) r.ativar();

    String desde = r.getProximaCompetencia();
    String anterior = Competencia.somarMeses(desde, -1);
    for (RecorrenciaItem i : atuais) {
      if (i.getVigenteDesde().compareTo(desde) >= 0) i.excluir(); // versão futura ainda não usada: substituída
      else if (i.vigenteEm(desde)) i.encerrarEm(anterior);
    }
    List<RecorrenciaItem> novos = salvarItens(r.getId(), e.itens(), desde);
    auditoria.registrar("EDITAR", ENTIDADE, r.getId(), antes, Map.of("regra", r.instantaneo(), "itens", e.itens()));
    return RegraSaida.de(r, novos);
  }

  /** Botão "gerar cobrança do ciclo" (uso de teste do job): competência do mês atual; repetir dá 409. */
  @Transactional
  public Cobranca gerarManual(UUID id) {
    Recorrencia r = obter(id);
    if (!r.isAtiva()) throw ApiException.conflito("REGRA_INATIVA", "Regra inativa não gera cobrança.");
    return gerarCiclo(r, Competencia.de(relogio.hoje()), Origem.MANUAL);
  }

  /** Gera a cobrança de uma competência. Chamado na transação de quem pede (manual ou job, por regra). */
  @Transactional
  public Cobranca gerarCiclo(Recorrencia r, String competencia, Origem origem) {
    Integer ja = jdbc.queryForObject(
        "SELECT count(*) FROM financeiro.recorrencia_geracoes WHERE recorrencia_id = ? AND competencia = ?",
        Integer.class, r.getId(), competencia);
    if (ja != null && ja > 0) {
      throw ApiException.conflito("CICLO_JA_GERADO", "A competência " + competencia + " já foi gerada para esta regra.");
    }
    List<RecorrenciaItem> vigentes = itens.daRegra(r.getId()).stream().filter(i -> i.vigenteEm(competencia)).toList();
    BigDecimal valor = vigentes.stream().map(RecorrenciaItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (valor.signum() <= 0) {
      throw ApiException.regra("SEM_ITENS_VIGENTES", "A regra não tem itens com valor vigentes em " + competencia + ".");
    }
    String descricao = (r.getDescricao() != null ? r.getDescricao() : "Mensalidade")
        + " — " + Competencia.mes(competencia).getMonthValue() + "/" + Competencia.mes(competencia).getYear();
    Cobranca c = Cobranca.nova(r.getEmpresaId(), r.getContratoId(), r.getId(), competencia, descricao,
        r.vencimentoDe(competencia), valor, null, null, null, r.getFormaPagamento());
    if (c.getDataVencimento().isBefore(relogio.hoje())) c.marcarAtrasada();
    cobrancaService.registrarNova(c);
    cobrancas.flush(); // a geração referencia a cobrança por FK
    jdbc.update("""
        INSERT INTO financeiro.recorrencia_geracoes
          (id, tenant_id, recorrencia_id, competencia, cobranca_id, origem, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        UUID.randomUUID(), TenantContexto.exigir(), r.getId(), competencia, c.getId(), origem.name(),
        Timestamp.from(relogio.agora()), Timestamp.from(relogio.agora()));
    Map<String, Object> antes = r.instantaneo();
    r.avancarDepoisDe(competencia);
    auditoria.registrar("GERAR_CICLO", ENTIDADE, r.getId(), antes,
        Map.of("competencia", competencia, "cobrancaId", c.getId(), "valor", valor, "origem", origem));
    return c;
  }

  /** Usado pelo job: regras ativas com competência a gerar até o mês atual. */
  @Transactional(readOnly = true)
  public List<UUID> pendentesDeGeracao() {
    return regras.ativasAte(Competencia.de(relogio.hoje())).stream().map(Recorrencia::getId).toList();
  }

  /**
   * Job, uma regra por transação: gera cada competência cujo dia de geração já chegou (recupera ciclos
   * perdidos se o serviço ficou fora). Competência já gerada (por exemplo, manualmente) só avança.
   */
  @Transactional
  public int processarRegraNoJob(UUID id) {
    Recorrencia r = regras.findById(id).orElseThrow();
    int geradas = 0;
    LocalDate hoje = relogio.hoje();
    for (int guarda = 0; guarda < 24 && r.isAtiva() && !r.dataDeGeracao(r.getProximaCompetencia()).isAfter(hoje); guarda++) {
      String competencia = r.getProximaCompetencia();
      if (r.getVigenciaFim() != null && Competencia.primeiroDia(competencia).isAfter(r.getVigenciaFim())) {
        r.encerrar(r.getVigenciaFim());
        break;
      }
      Integer ja = jdbc.queryForObject(
          "SELECT count(*) FROM financeiro.recorrencia_geracoes WHERE recorrencia_id = ? AND competencia = ?",
          Integer.class, r.getId(), competencia);
      if (ja != null && ja > 0) {
        r.avancarDepoisDe(competencia);
        continue;
      }
      gerarCiclo(r, competencia, Origem.JOB);
      geradas++;
    }
    return geradas;
  }

  /** Contrato assinado (consumidor idempotente): cria a regra inicial, se o contrato ainda não tem uma. */
  @Transactional
  public boolean criarDeContrato(UUID contratoId, RegraEntrada e) {
    if (regras.doContrato(contratoId).isPresent()) return false;
    criar(e);
    return true;
  }

  /** Contrato cancelado: a regra para de gerar e o valor entra como churn no MRR do mês (RN-43). */
  @Transactional
  public boolean encerrarDeContrato(UUID contratoId, LocalDate quando) {
    return regras.doContrato(contratoId).map(r -> {
      Map<String, Object> antes = r.instantaneo();
      r.encerrar(quando);
      auditoria.registrar("ENCERRAR", ENTIDADE, r.getId(), antes, r.instantaneo());
      return true;
    }).orElse(false);
  }

  private static void configurar(Recorrencia r, RegraEntrada e) {
    r.configurar(e.clienteId(), e.contratoId(), e.descricao(),
        e.periodicidade() != null ? e.periodicidade() : Periodicidade.MENSAL,
        e.diaGeracao() != null ? e.diaGeracao() : 5,
        e.diasParaVencimento() != null ? e.diasParaVencimento() : 10,
        e.formaPagamento());
  }

  private List<RecorrenciaItem> salvarItens(UUID regra, List<ItemEntrada> entrada, String desde) {
    return itens.saveAll(entrada.stream().map(i -> new RecorrenciaItem(regra, i.servicoId(), i.descricao().trim(),
        i.quantidade(), i.unidade().trim(), i.valorUnitario(), i.franquia(), desde)).toList());
  }
}
