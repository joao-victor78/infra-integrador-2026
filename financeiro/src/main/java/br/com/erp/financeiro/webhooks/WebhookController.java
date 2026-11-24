package br.com.erp.financeiro.webhooks;

import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.pagamentos.provedor.ProvedorPagamento;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota pública, sem JWT, sob /public/** (RN-25, Contrato §12.6). Só recebe: a resposta nunca traz dado
 * de negócio (RN-26). Assinatura inválida: 400 e nada muda (RN-27); evento repetido: 200 (RN-28).
 */
@Tag(name = "webhooks")
@RestController
public class WebhookController {

  private final WebhookService webhooks;

  public WebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  /** contratos/financeiro.yaml, WebhookPagamentoEntrada. */
  public record WebhookPagamentoEntrada(
      @NotBlank @Size(max = 100) String eventoId,
      @NotBlank String assinatura,
      @NotNull @Pattern(regexp = "pagamento\\.(confirmado|estornado|falhou)") String tipo,
      @NotNull UUID cobrancaId,
      UUID pagamentoId,
      @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorPago,
      OffsetDateTime dataPagamento) {}

  @PostMapping("/public/financeiro/webhooks/pagamento")
  public Resposta<Map<String, Boolean>> receber(@Valid @RequestBody WebhookPagamentoEntrada e) {
    webhooks.receber(new ProvedorPagamento.WebhookRecebido(e.eventoId(), e.assinatura(), e.tipo(), e.cobrancaId(),
        e.pagamentoId(), e.valorPago(), e.dataPagamento()));
    return Resposta.ok(Map.of("recebido", true));
  }

  @ExceptionHandler(ProvedorPagamento.AssinaturaInvalidaException.class)
  ResponseEntity<Resposta<Void>> assinaturaInvalida(ProvedorPagamento.AssinaturaInvalidaException e) {
    return ResponseEntity.badRequest().body(Resposta.falha("Assinatura inválida."));
  }
}
