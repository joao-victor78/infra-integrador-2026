package br.com.erp.financeiro.recorrencias;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecorrenciaItemRepository extends JpaRepository<RecorrenciaItem, UUID> {

  @Query("select i from RecorrenciaItem i where i.deletedAt is null and i.recorrenciaId = :regra order by i.createdAt, i.id")
  List<RecorrenciaItem> daRegra(UUID regra);

  @Query("select i from RecorrenciaItem i where i.deletedAt is null and i.recorrenciaId in :regras order by i.createdAt, i.id")
  List<RecorrenciaItem> dasRegras(Collection<UUID> regras);
}
