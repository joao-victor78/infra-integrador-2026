package br.com.erp.financeiro.tenant;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Entrega ao Hibernate o tenant da operação. Toda entidade com @TenantId é filtrada por ele nas
 * consultas JPQL/derivadas e preenchida com ele ao gravar. SQL nativo filtra tenant_id à mão.
 */
@Component
public class ResolvedorDeTenant implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

  /** Sem tenant no contexto, as consultas procuram por este valor e não encontram nada. */
  public static final UUID NENHUM = new UUID(0L, 0L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return TenantContexto.atual().orElse(NENHUM);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  @Override
  public void customize(Map<String, Object> propriedades) {
    propriedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
