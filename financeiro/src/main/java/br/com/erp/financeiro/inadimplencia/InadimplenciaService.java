package br.com.erp.financeiro.inadimplencia;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaDtos.CobrancaSaida;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.pagamentos.PagamentoRepository;
import br.com.erp.financeiro.pagamentos.PagamentoSaida;
import br.com.erp.financeiro.regua.ReguaDtos.Disparo;
import br.com.erp.financeiro.regua.ReguaService;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inadimplência (RF-07): calculada na consulta (RN-41). PARCIAL entra pelo saldo restante (RN-39).
 * O histórico de uma empresa reúne pagamentos e disparos da régua (RN-40).
 */
@Service
public class InadimplenciaService {

  private final CobrancaRepository cobrancas;
  private final PagamentoRepository pagamentos;
  private final PromessaRepository promessas;
  private final ReguaService regua;
  private final CobrancaService cobrancaService;
  private final AuditoriaService auditoria;
  private final Relogio relogio;

  public InadimplenciaService(CobrancaRepository cobrancas, PagamentoRepository pagamentos, PromessaRepository promessas,
      ReguaService regua, CobrancaService cobrancaService, AuditoriaService auditoria, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.pagamentos = pagamentos;
    this.promessas = promessas;
    this.regua = regua;
    this.cobrancaService = cobrancaService;
    this.auditoria = auditoria;
    this.relogio = relogio;
  }

  public record Item(UUID cobrancaId, UUID clienteId, LocalDate vencimento, long diasAtraso, FaixaAging faixa,
      BigDecimal saldoDevedor, StatusCobranca status, String descricao) {}

  public record Empresa(UUID clienteId, int cobrancas, long maiorAtraso, BigDecimal saldoDevedor) {}

  public record Painel(List<Item> itens, Map<String, BigDecimal> totalPorFaixa, List<Empresa> empresas,
      BigDecimal total, int pagina, int tamanho, long totalItens) {}

  public enum Ordem { DIAS, VALOR }

  @Transactional(readOnly = true)
  public Painel painel(FaixaAging faixa, Ordem ordem, int pagina, int tamanho) {
    LocalDate hoje = relogio.hoje();
    List<Item> todos = cobrancas.vencidasEm(StatusCobranca.EM_ABERTO, hoje).stream()
        .filter(c -> c.saldo().signum() > 0)
        .map(c -> new Item(c.getId(), c.getEmpresaId(), c.getDataVencimento(), c.diasAtraso(hoje),
            FaixaAging.de(c.diasAtraso(hoje)), c.saldo(), c.getStatus(), c.getDescricao()))
        .toList();

    Map<String, BigDecimal> porFaixa = new LinkedHashMap<>();
    for (FaixaAging f : FaixaAging.values()) porFaixa.put(f.rotulo(), Dinheiro.ZERO);
    todos.forEach(i -> porFaixa.merge(i.faixa().rotulo(), i.saldoDevedor(), BigDecimal::add));

    List<Item> filtrados = new ArrayList<>(faixa == null ? todos : todos.stream().filter(i -> i.faixa() == faixa).toList());
    Comparator<Item> cmp = ordem == Ordem.VALOR
        ? Comparator.comparing(Item::saldoDevedor).reversed()
        : Comparator.comparingLong(Item::diasAtraso).reversed();
    filtrados.sort(cmp.thenComparing(Item::cobrancaId));

    List<Empresa> empresas = filtrados.stream().collect(Collectors.groupingBy(Item::clienteId, LinkedHashMap::new, Collectors.toList()))
        .entrySet().stream()
        .map(e -> new Empresa(e.getKey(), e.getValue().size(),
            e.getValue().stream().mapToLong(Item::diasAtraso).max().orElse(0),
            e.getValue().stream().map(Item::saldoDevedor).reduce(Dinheiro.ZERO, BigDecimal::add)))
        .sorted(ordem == Ordem.VALOR ? Comparator.comparing(Empresa::saldoDevedor).reversed()
            : Comparator.comparingLong(Empresa::maiorAtraso).reversed())
        .toList();

    int de = Math.min(pagina * tamanho, filtrados.size());
    int ate = Math.min(de + tamanho, filtrados.size());
    BigDecimal total = filtrados.stream().map(Item::saldoDevedor).reduce(Dinheiro.ZERO, BigDecimal::add);
    return new Painel(filtrados.subList(de, ate), porFaixa, empresas, total, pagina, tamanho, filtrados.size());
  }

  public record Historico(UUID clienteId, List<CobrancaSaida> cobrancas, List<PagamentoSaida> pagamentos,
      List<Disparo> disparos, List<PromessaSaida> promessas) {}

  /** RN-40: pagamentos e disparos da régua da empresa, com as cobranças e promessas. */
  @Transactional(readOnly = true)
  public Historico historico(UUID empresaId) {
    LocalDate hoje = relogio.hoje();
    List<Cobranca> daEmpresa = cobrancas.daEmpresa(empresaId);
    List<UUID> ids = daEmpresa.stream().map(Cobranca::getId).toList();
    List<Disparo> disparos = new ArrayList<>();
    List<PromessaSaida> prom = new ArrayList<>();
    for (UUID id : ids) {
      disparos.addAll(regua.disparosDaCobranca(id));
      promessas.daCobranca(id).forEach(p -> prom.add(PromessaSaida.de(p)));
    }
    disparos.sort(Comparator.comparing(Disparo::disparadoEm).reversed());
    return new Historico(empresaId, daEmpresa.stream().map(c -> CobrancaSaida.de(c, hoje)).toList(),
        ids.isEmpty() ? List.of() : pagamentos.dasCobrancas(ids).stream().map(PagamentoSaida::de).toList(),
        disparos, prom);
  }

  public record PromessaEntrada(
      @NotNull(message = "obrigatória") LocalDate dataPrometida,
      @NotNull(message = "obrigatório") @Positive(message = "deve ser maior que zero")
      @Digits(integer = 13, fraction = 2, message = "no máximo 2 casas decimais") BigDecimal valor,
      @Size(max = 500, message = "até 500 caracteres") String observacao) {}

  public record PromessaSaida(UUID id, UUID cobrancaId, LocalDate dataPrometida, BigDecimal valor, String observacao,
      PromessaPagamento.Status status, UUID createdBy, OffsetDateTime createdAt) {

    static PromessaSaida de(PromessaPagamento p) {
      return new PromessaSaida(p.getId(), p.getCobrancaId(), p.getDataPrometida(), p.getValor(), p.getObservacao(),
          p.getStatus(), p.getCreatedBy(), p.getCreatedAt());
    }
  }

  /** RN-42 (desejável): registrar a data prometida; o job da régua marca as não cumpridas como QUEBRADA. */
  @Transactional
  public PromessaSaida registrarPromessa(UUID cobrancaId, PromessaEntrada e) {
    Cobranca c = cobrancaService.obter(cobrancaId);
    if (!c.emAberto()) throw ApiException.conflito("COBRANCA_SEM_SALDO", "Só cobrança em aberto aceita promessa de pagamento.");
    if (e.dataPrometida().isBefore(relogio.hoje())) {
      throw ApiException.campo("dataPrometida", "DATA_PASSADA", "A data prometida não pode estar no passado.");
    }
    if (e.valor().compareTo(c.saldo()) > 0) {
      throw ApiException.regra("VALOR_ACIMA_DO_SALDO", "A promessa não pode passar do saldo em aberto (" + Dinheiro.brl(c.saldo()) + ").");
    }
    PromessaPagamento p = promessas.save(new PromessaPagamento(c.getId(), e.dataPrometida(), e.valor(),
        e.observacao() == null || e.observacao().isBlank() ? null : e.observacao().trim()));
    auditoria.registrar("REGISTRAR_PROMESSA", "promessa_pagamento", p.getId(), null, Map.of(
        "cobrancaId", c.getId(), "dataPrometida", e.dataPrometida(), "valor", e.valor()));
    return PromessaSaida.de(p);
  }

  @Transactional(readOnly = true)
  public List<PromessaSaida> promessasDaCobranca(UUID cobrancaId) {
    cobrancaService.obter(cobrancaId);
    return promessas.daCobranca(cobrancaId).stream().map(PromessaSaida::de).toList();
  }
}
