package br.com.erp.financeiro.regua;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReguaEtapaRepository extends JpaRepository<ReguaEtapa, UUID> {

  @Query("select e from ReguaEtapa e where e.deletedAt is null order by e.deslocamentoDias")
  List<ReguaEtapa> configuradas();

  @Query("select e from ReguaEtapa e where e.deletedAt is null and e.ativa = true order by e.deslocamentoDias")
  List<ReguaEtapa> ativas();
}
