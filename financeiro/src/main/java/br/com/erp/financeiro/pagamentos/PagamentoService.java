package br.com.erp.financeiro.pagamentos;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.auditoria.AuditoriaService;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.cobrancas.MeioPagamento;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Tenant;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pedido de PIX, boleto ou link de cartão pelo adaptador de provedor (RF-04). */
@Service
public class PagamentoService {

  private final PagamentoRepository pagamentos;
  private final CobrancaService cobrancas;
  private final ProvedorPagamento provedor;
  private final AuditoriaService auditoria;

  public PagamentoService(PagamentoRepository pagamentos, CobrancaService cobrancas, ProvedorPagamento provedor,
      AuditoriaService auditoria) {
    this.pagamentos = pagamentos;
    this.cobrancas = cobrancas;
    this.provedor = provedor;
    this.auditoria = auditoria;
  }

  /** O valor pedido é sempre o saldo em aberto. Cobrança PAGO ou CANCELADO é recusada (RF-04, critério 3). */
  @Transactional
  public Pagamento solicitar(UUID cobrancaId, MeioPagamento meio) {
    Cobranca c = cobrancas.obter(cobrancaId);
    if (c.getDeletedAt() != null || !StatusCobranca.PAGAVEIS.contains(c.getStatus()) || c.saldo().signum() == 0) {
      throw ApiException.conflito("COBRANCA_NAO_PAGAVEL",
          "Cobrança " + c.getStatus() + " não aceita novo pedido de pagamento.");
    }
    Pagamento p = Pagamento.pedido(c.getId(), meio, c.saldo(), provedor.nome());
    p.registrarResposta(provedor.createCharge(meio, new ProvedorPagamento.Solicitacao(
        p.getId(), c.getId(), c.saldo(), c.getDescricao(), c.getDataVencimento())));
    pagamentos.save(p);
    auditoria.registrar("SOLICITAR_PAGAMENTO", "pagamento", p.getId(), null, p.instantaneo());
    return p;
  }

  @Transactional(readOnly = true)
  public Pagamento obter(UUID id) {
    return Tenant.doTenant(pagamentos.findById(id), "Pagamento");
  }

  @Transactional(readOnly = true)
  public List<Pagamento> daCobranca(UUID cobrancaId) {
    cobrancas.obter(cobrancaId); // 404 se for de outro tenant
    return pagamentos.daCobranca(cobrancaId);
  }
}
