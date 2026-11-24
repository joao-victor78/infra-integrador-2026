package br.com.erp.financeiro.cobrancas;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Consultas JPQL: o Hibernate acrescenta tenant_id = tenant do contexto (@TenantId). */
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID>, JpaSpecificationExecutor<Cobranca> {

  /** RN-06: ABERTO ou PARCIAL com vencimento anterior a hoje. */
  @Query("select c from Cobranca c where c.deletedAt is null and c.status in :status and c.dataVencimento < :hoje")
  List<Cobranca> vencidasEm(Collection<StatusCobranca> status, LocalDate hoje);

  @Query("select c from Cobranca c where c.deletedAt is null and c.status in :status and c.dataVencimento = :data")
  List<Cobranca> vencendoEm(Collection<StatusCobranca> status, LocalDate data);

  @Query("select c from Cobranca c where c.deletedAt is null and c.status in :status order by c.dataVencimento")
  List<Cobranca> comStatus(Collection<StatusCobranca> status);

  @Query("select c from Cobranca c where c.deletedAt is null and c.empresaId in :empresas and c.status in :status")
  List<Cobranca> daEmpresasComStatus(Collection<UUID> empresas, Collection<StatusCobranca> status);

  @Query("select c from Cobranca c where c.empresaId = :empresa order by c.dataVencimento desc")
  List<Cobranca> daEmpresa(UUID empresa);

  @Query("select c from Cobranca c where c.deletedAt is null and c.recorrenciaId is not null and c.status <> br.com.erp.financeiro.cobrancas.StatusCobranca.CANCELADO")
  List<Cobranca> recorrentes();

  /** Busca global (RF-09): descrição ou início do id. */
  @Query("""
      select c from Cobranca c
       where c.deletedAt is null
         and (lower(coalesce(c.descricao, '')) like :termo escape '\\' or lower(cast(c.id as string)) like :prefixo)
       order by c.dataVencimento desc""")
  List<Cobranca> buscar(String termo, String prefixo, Pageable limite);
}
