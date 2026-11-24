package br.com.erp.financeiro.recorrencias;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RecorrenciaRepository extends JpaRepository<Recorrencia, UUID>, JpaSpecificationExecutor<Recorrencia> {

  @Query("select r from Recorrencia r where r.deletedAt is null and r.ativa = true and r.proximaCompetencia <= :competencia")
  List<Recorrencia> ativasAte(String competencia);

  @Query("select r from Recorrencia r where r.deletedAt is null and r.contratoId = :contrato")
  Optional<Recorrencia> doContrato(UUID contrato);

  @Query("select r from Recorrencia r where r.deletedAt is null")
  List<Recorrencia> todas();
}
