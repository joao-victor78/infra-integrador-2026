package br.com.erp.financeiro.inadimplencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromessaRepository extends JpaRepository<PromessaPagamento, UUID> {

  @Query("select p from PromessaPagamento p where p.deletedAt is null and p.cobrancaId = :cobranca order by p.dataPrometida")
  List<PromessaPagamento> daCobranca(UUID cobranca);

  @Query("""
      select p from PromessaPagamento p where p.deletedAt is null
         and p.status = br.com.erp.financeiro.inadimplencia.PromessaPagamento.Status.PENDENTE
         and p.cobrancaId = :cobranca""")
  List<PromessaPagamento> pendentesDaCobranca(UUID cobranca);

  @Query("""
      select p from PromessaPagamento p where p.deletedAt is null
         and p.status = br.com.erp.financeiro.inadimplencia.PromessaPagamento.Status.PENDENTE
         and p.dataPrometida < :hoje""")
  List<PromessaPagamento> vencidas(LocalDate hoje);
}
