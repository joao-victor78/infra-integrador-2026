package br.com.erp.financeiro.indicadores;

import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Competencia;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.contaspagar.ContaPagarRepository;
import br.com.erp.financeiro.pagamentos.PagamentoRepository;
import br.com.erp.financeiro.recorrencias.Recorrencia;
import br.com.erp.financeiro.recorrencias.RecorrenciaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MRR decomposto e home do financeiro (RF-08). O Financeiro é o dono do cálculo (RN-45, PD-3).
 *
 * MRR de uma empresa no mês M: soma, das cobranças geradas por recorrência que cobrem M, do valor
 * (menos desconto) dividido pelos meses da periodicidade. Regra encerrada (contrato cancelado) até M
 * não conta em M — por isso o cancelamento entra como Churned no próprio mês (RF-08, critério 2).
 */
@Service
public class IndicadoresService {

  private final CobrancaRepository cobrancas;
  private final RecorrenciaRepository regras;
  private final PagamentoRepository pagamentos;
  private final ContaPagarRepository contasPagar;
  private final Relogio relogio;

  public IndicadoresService(CobrancaRepository cobrancas, RecorrenciaRepository regras, PagamentoRepository pagamentos,
      ContaPagarRepository contasPagar, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.regras = regras;
    this.pagamentos = pagamentos;
    this.contasPagar = contasPagar;
    this.relogio = relogio;
  }

  /** contratos/financeiro.yaml MrrResponse, com ARR e churn (RN-47, RN-48) acrescentados. Taxas em pontos (12.5 = 12,5%). */
  public record Mrr(String periodo, BigDecimal mrrNovo, BigDecimal mrrExpansion, BigDecimal mrrContraction,
      BigDecimal mrrChurned, BigDecimal mrrNetNew, BigDecimal mrrTotal, BigDecimal mrrInicial, BigDecimal arr,
      int empresasAtivas, int empresasNoInicio, int empresasCanceladas, BigDecimal logoChurn, BigDecimal mrrChurn,
      BigDecimal revenueChurn) {}

  @Transactional(readOnly = true)
  public Mrr mrr(YearMonth periodo) {
    Base base = carregar();
    return calcular(base, periodo);
  }

  @Transactional(readOnly = true)
  public List<Mrr> historico(YearMonth ate, int meses) {
    Base base = carregar();
    List<Mrr> lista = new ArrayList<>();
    for (int i = meses - 1; i >= 0; i--) lista.add(calcular(base, ate.minusMonths(i)));
    return lista;
  }

  private record Base(List<Cobranca> recorrentes, Map<UUID, Recorrencia> regras) {}

  private Base carregar() {
    Map<UUID, Recorrencia> porId = regras.todas().stream().collect(Collectors.toMap(Recorrencia::getId, Function.identity()));
    return new Base(cobrancas.recorrentes(), porId);
  }

  private Mrr calcular(Base b, YearMonth m) {
    Map<UUID, BigDecimal> atual = porEmpresa(b, m);
    Map<UUID, BigDecimal> anterior = porEmpresa(b, m.minusMonths(1));
    BigDecimal novo = Dinheiro.ZERO, expansion = Dinheiro.ZERO, contraction = Dinheiro.ZERO, churned = Dinheiro.ZERO;
    Set<UUID> empresas = new HashSet<>(atual.keySet());
    empresas.addAll(anterior.keySet());
    int canceladas = 0;
    for (UUID e : empresas) {
      BigDecimal antes = anterior.getOrDefault(e, BigDecimal.ZERO);
      BigDecimal agora = atual.getOrDefault(e, BigDecimal.ZERO);
      if (antes.signum() == 0 && agora.signum() > 0) novo = novo.add(agora);                       // RN-43
      else if (antes.signum() > 0 && agora.signum() == 0) {
        churned = churned.add(antes);
        canceladas++;
      } else if (agora.compareTo(antes) > 0) expansion = expansion.add(agora.subtract(antes));
      else if (agora.compareTo(antes) < 0) contraction = contraction.add(antes.subtract(agora));
    }
    BigDecimal total = soma(atual.values());
    BigDecimal inicial = soma(anterior.values());
    BigDecimal netNew = novo.add(expansion).subtract(contraction).subtract(churned);                // RN-44
    int noInicio = (int) anterior.values().stream().filter(v -> v.signum() > 0).count();
    return new Mrr(m.toString(), dinheiro(novo), dinheiro(expansion), dinheiro(contraction), dinheiro(churned),
        dinheiro(netNew), dinheiro(total), dinheiro(inicial), dinheiro(total.multiply(BigDecimal.valueOf(12))),  // RN-47
        (int) atual.values().stream().filter(v -> v.signum() > 0).count(), noInicio, canceladas,
        taxa(BigDecimal.valueOf(canceladas), BigDecimal.valueOf(noInicio)),                                    // RN-48
        taxa(churned, inicial), taxa(churned.add(contraction), inicial));
  }

  private static Map<UUID, BigDecimal> porEmpresa(Base b, YearMonth m) {
    Map<UUID, BigDecimal> mapa = new HashMap<>();
    for (Cobranca c : b.recorrentes()) {
      Recorrencia r = b.regras().get(c.getRecorrenciaId());
      if (r == null) continue;
      YearMonth inicio = Competencia.mes(c.getCompetencia());
      int meses = r.getPeriodicidade().meses();
      if (m.isBefore(inicio) || !m.isBefore(inicio.plusMonths(meses))) continue;
      if (r.getVigenciaFim() != null && !YearMonth.from(r.getVigenciaFim()).isAfter(m)) continue;
      BigDecimal mensal = c.getValor().subtract(c.getDesconto()).divide(BigDecimal.valueOf(meses), 6, RoundingMode.HALF_EVEN);
      mapa.merge(c.getEmpresaId(), mensal, BigDecimal::add);
    }
    return mapa;
  }

  private static BigDecimal soma(Iterable<BigDecimal> valores) {
    BigDecimal s = BigDecimal.ZERO;
    for (BigDecimal v : valores) s = s.add(v);
    return s;
  }

  private static BigDecimal dinheiro(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_EVEN);
  }

  private static BigDecimal taxa(BigDecimal parte, BigDecimal todo) {
    return todo.signum() == 0 ? BigDecimal.ZERO.setScale(2)
        : parte.multiply(BigDecimal.valueOf(100)).divide(todo, 2, RoundingMode.HALF_EVEN);
  }

  /** contratos/financeiro.yaml DashboardFinanceiro (RN-46), com contagens e MRR do mês. */
  public record Dashboard(BigDecimal aReceberHoje, BigDecimal atrasados, BigDecimal pagamentosRecebidosMes,
      BigDecimal contasAPagarMes, BigDecimal inadimplenciaTotal, int cobrancasEmAberto, int cobrancasAtrasadas,
      BigDecimal mrrAtual, BigDecimal arr) {}

  @Transactional(readOnly = true)
  public Dashboard dashboard() {
    LocalDate hoje = relogio.hoje();
    YearMonth mes = YearMonth.from(hoje);
    List<Cobranca> abertas = cobrancas.comStatus(StatusCobranca.EM_ABERTO);
    BigDecimal aReceberHoje = Dinheiro.ZERO, atrasados = Dinheiro.ZERO, inadimplencia = Dinheiro.ZERO;
    int qtdAtrasadas = 0;
    for (Cobranca c : abertas) {
      if (c.getDataVencimento().equals(hoje)) aReceberHoje = aReceberHoje.add(c.saldo());
      if (c.getStatus() == StatusCobranca.ATRASADO) {
        atrasados = atrasados.add(c.saldo());
        qtdAtrasadas++;
      }
      if (c.getDataVencimento().isBefore(hoje)) inadimplencia = inadimplencia.add(c.saldo());
    }
    OffsetDateTime de = mes.atDay(1).atStartOfDay(Relogio.BRASILIA).toOffsetDateTime();
    OffsetDateTime ate = mes.plusMonths(1).atDay(1).atStartOfDay(Relogio.BRASILIA).toOffsetDateTime();
    Mrr mrr = mrr(mes);
    return new Dashboard(dinheiro(aReceberHoje), dinheiro(atrasados), dinheiro(pagamentos.totalConfirmado(de, ate)),
        dinheiro(contasPagar.totalAPagar(mes.atDay(1), mes.atEndOfMonth())), dinheiro(inadimplencia),
        abertas.size(), qtdAtrasadas, mrr.mrrTotal(), mrr.arr());
  }
}
