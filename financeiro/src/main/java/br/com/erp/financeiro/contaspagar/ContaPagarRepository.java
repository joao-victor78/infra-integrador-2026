package br.com.erp.financeiro.contaspagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, UUID>, JpaSpecificationExecutor<ContaPagar> {

  /** Home do financeiro (RN-46): contas a pagar do mês, ainda não pagas. */
  @Query("""
      select coalesce(sum(c.valor), 0) from ContaPagar c
       where c.deletedAt is null
         and c.status in (br.com.erp.financeiro.contaspagar.ContaPagar.Status.PENDENTE,
                          br.com.erp.financeiro.contaspagar.ContaPagar.Status.APROVADA)
         and c.dataVencimento between :de and :ate""")
  BigDecimal totalAPagar(LocalDate de, LocalDate ate);

  @Query("""
      select c from ContaPagar c where c.deletedAt is null
         and (lower(c.fornecedorNome) like :termo escape '\\' or lower(coalesce(c.documento, '')) like :termo escape '\\'
              or lower(cast(c.id as string)) like :prefixo)
       order by c.dataVencimento desc""")
  List<ContaPagar> buscar(String termo, String prefixo, Pageable limite);
}
