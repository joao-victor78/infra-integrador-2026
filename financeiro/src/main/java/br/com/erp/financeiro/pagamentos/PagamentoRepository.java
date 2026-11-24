package br.com.erp.financeiro.pagamentos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

  @Query("select p from Pagamento p where p.cobrancaId = :cobranca order by p.createdAt desc")
  List<Pagamento> daCobranca(UUID cobranca);

  @Query("select p from Pagamento p where p.cobrancaId in :cobrancas order by p.createdAt desc")
  List<Pagamento> dasCobrancas(Collection<UUID> cobrancas);

  @Query("""
      select coalesce(sum(p.valor), 0) from Pagamento p
       where p.status = br.com.erp.financeiro.pagamentos.StatusPagamento.CONFIRMADO
         and p.confirmadoEm >= :de and p.confirmadoEm < :ate""")
  BigDecimal totalConfirmado(OffsetDateTime de, OffsetDateTime ate);
}
