package br.com.erp.financeiro.contaspagar;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.comum.Competencia;
import br.com.erp.financeiro.comum.Relogio;
import br.com.erp.financeiro.comum.Tenant;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.ContaPagarEntrada;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.ContaPagarSaida;
import br.com.erp.financeiro.contaspagar.ContaPagarDtos.PagamentoContaEntrada;
import br.com.erp.financeiro.seguranca.Usuarios;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar (RF-02): lançar, aprovar (permissão distinta, RN-11) e registrar o pagamento (RN-12). */
@Service
public class ContaPagarService {

  static final String ENTIDADE = "conta_pagar";

  private final ContaPagarRepository contas;
  private final AuditoriaService auditoria;
  private final Relogio relogio;

  public ContaPagarService(ContaPagarRepository contas, AuditoriaService auditoria, Relogio relogio) {
    this.contas = contas;
    this.auditoria = auditoria;
    this.relogio = relogio;
  }

  @Transactional(readOnly = true)
  public Pagina<ContaPagarSaida> listar(Boolean aprovado, ContaPagar.Status status, Pageable pagina) {
    Specification<ContaPagar> s = (r, q, cb) -> cb.isNull(r.get("deletedAt"));
    if (aprovado != null) {
      s = s.and((r, q, cb) -> aprovado
          ? r.get("status").in(ContaPagar.Status.APROVADA, ContaPagar.Status.PAGA)
          : cb.equal(r.get("status"), ContaPagar.Status.PENDENTE));
    }
    if (status != null) s = s.and((r, q, cb) -> cb.equal(r.get("status"), status));
    return Pagina.de(contas.findAll(s, pagina), ContaPagarSaida::de);
  }

  @Transactional(readOnly = true)
  public ContaPagar obter(UUID id) {
    return Tenant.ativoDoTenant(contas.findById(id), "Conta a pagar");
  }

  @Transactional
  public ContaPagar lancar(ContaPagarEntrada e) {
    ContaPagar c = contas.save(new ContaPagar(e.fornecedorNome().trim(), vazioNulo(e.fornecedorDocumento()),
        vazioNulo(e.categoria()), vazioNulo(e.centroCusto()), Competencia.de(e.competencia()), e.vencimento(), e.valor(),
        vazioNulo(e.documento()), vazioNulo(e.observacoes())));
    auditoria.registrar("CRIAR", ENTIDADE, c.getId(), null, c.instantaneo());
    return c;
  }

  @Transactional
  public ContaPagar aprovar(UUID id) {
    ContaPagar c = obter(id);
    if (c.getStatus() != ContaPagar.Status.PENDENTE) {
      throw ApiException.conflito("CONTA_NAO_PENDENTE", "Só conta pendente pode ser aprovada (está " + c.getStatus() + ").");
    }
    Map<String, Object> antes = c.instantaneo();
    c.aprovar(Usuarios.atual(), relogio.agoraUtc());
    auditoria.registrar("APROVAR", ENTIDADE, c.getId(), antes, c.instantaneo());
    return c;
  }

  /** RN-12: só depois de aprovada. 422 para conta não aprovada (EOR, RF-02). */
  @Transactional
  public ContaPagar registrarPagamento(UUID id, PagamentoContaEntrada e) {
    ContaPagar c = obter(id);
    if (c.getStatus() == ContaPagar.Status.PENDENTE) {
      throw ApiException.regra("CONTA_NAO_APROVADA", "A conta precisa ser aprovada antes do pagamento.");
    }
    if (c.getStatus() != ContaPagar.Status.APROVADA) {
      throw ApiException.conflito("CONTA_NAO_PAGAVEL", "Conta " + c.getStatus() + " não aceita pagamento.");
    }
    LocalDate data = e == null || e.dataPagamento() == null ? relogio.hoje() : e.dataPagamento();
    if (data.isAfter(relogio.hoje())) throw ApiException.campo("dataPagamento", "DATA_FUTURA", "A data do pagamento não pode ser futura.");
    Map<String, Object> antes = c.instantaneo();
    c.registrarPagamento(e == null || e.valorPago() == null ? c.getValor() : e.valorPago(), data);
    auditoria.registrar("BAIXAR", ENTIDADE, c.getId(), antes, c.instantaneo());
    return c;
  }

  private static String vazioNulo(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
