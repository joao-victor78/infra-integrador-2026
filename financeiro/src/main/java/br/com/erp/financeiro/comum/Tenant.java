package br.com.erp.financeiro.comum;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.tenant.TenantContexto;
import java.util.Optional;

/** Carregamento por id que nunca atravessa tenant: registro de outro tenant é 404, nunca 403. */
public final class Tenant {

  private Tenant() {}

  public static <T extends EntidadeBase> T doTenant(Optional<T> registro, String recurso) {
    return registro
        .filter(r -> r.getTenantId() != null && r.getTenantId().equals(TenantContexto.exigir()))
        .orElseThrow(() -> ApiException.naoEncontrado(recurso));
  }

  /** Como {@link #doTenant}, e também esconde o que foi excluído logicamente. */
  public static <T extends EntidadeBase> T ativoDoTenant(Optional<T> registro, String recurso) {
    T r = doTenant(registro, recurso);
    if (r.getDeletedAt() != null) throw ApiException.naoEncontrado(recurso);
    return r;
  }
}
