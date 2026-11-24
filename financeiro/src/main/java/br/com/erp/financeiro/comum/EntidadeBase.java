package br.com.erp.financeiro.comum;

import br.com.erp.financeiro.seguranca.Usuarios;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/**
 * As sete colunas obrigatórias de toda tabela de negócio (Contrato §7.2, EOR §2.3).
 * {@code @TenantId}: o Hibernate grava o tenant do contexto e filtra as consultas por ele.
 * Id gerado pela aplicação; {@link Persistable} evita o SELECT antes de cada INSERT.
 */
@MappedSuperclass
public abstract class EntidadeBase implements Persistable<UUID> {

  @Id
  private UUID id = UUID.randomUUID();

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Transient
  private boolean novo = true;

  @PrePersist
  void aoCriar() {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    createdAt = agora;
    updatedAt = agora;
    createdBy = Usuarios.atual();
    updatedBy = createdBy;
  }

  @PreUpdate
  void aoAlterar() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    updatedBy = Usuarios.atual();
  }

  @PostLoad
  @PostPersist
  void marcarPersistido() {
    novo = false;
  }

  /** Exclusão lógica (EOR §2.3): o registro sai das consultas e o histórico permanece. */
  public void excluir() {
    deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return novo;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}
