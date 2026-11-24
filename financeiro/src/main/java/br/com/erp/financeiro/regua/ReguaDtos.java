package br.com.erp.financeiro.regua;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** contratos/financeiro.yaml: ReguaEtapa, ReguaCobranca. */
public final class ReguaDtos {

  private ReguaDtos() {}

  public record Etapa(
      UUID id,
      @NotNull(message = "obrigatório") @Min(value = -60, message = "entre -60 e 120") @Max(value = 120, message = "entre -60 e 120")
      Integer diasOffset,
      @Size(max = 60, message = "até 60 caracteres") String acao,
      @NotNull(message = "obrigatório") ReguaEtapa.Canal canal,
      @NotBlank(message = "o modelo de mensagem não pode ser vazio") @Size(max = 2000, message = "até 2000 caracteres")
      String templateMensagem,
      Boolean ativa) {

    static Etapa de(ReguaEtapa e) {
      return new Etapa(e.getId(), e.getDeslocamentoDias(), e.getAcao(), e.getCanal(), e.getModeloMensagem(), e.isAtiva());
    }
  }

  public record Regua(@NotNull(message = "obrigatório") List<@Valid Etapa> etapas) {}

  /** Disparo registrado (RN-32), para o histórico por cobrança. */
  public record Disparo(UUID id, UUID cobrancaId, UUID etapaId, Integer diasOffset, String canal, String situacao,
      OffsetDateTime disparadoEm) {}

  public record Execucao(int marcadasAtrasadas, int eventosDeAtraso, int disparos, int promessasQuebradas) {}
}
