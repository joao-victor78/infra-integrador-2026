package br.com.erp.financeiro.auditoria;

import br.com.erp.financeiro.api.Pagina;
import br.com.erp.financeiro.api.Resposta;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da auditoria, restrita a administradores (RN-68). */
@Tag(name = "auditoria")
@RestController
public class AuditoriaController {

  private final AuditoriaService auditoria;

  public AuditoriaController(AuditoriaService auditoria) {
    this.auditoria = auditoria;
  }

  @GetMapping("/api/financeiro/auditoria")
  @PreAuthorize("hasAuthority('financeiro.auditoria.ver')")
  public Resposta<Pagina<AuditoriaService.Registro>> listar(
      @RequestParam(required = false) String entidade,
      @RequestParam(required = false) UUID registroId,
      @RequestParam(required = false) Integer pagina,
      @RequestParam(required = false) Integer tamanho) {
    Pageable p = Pagina.pedido(pagina, tamanho, Sort.unsorted());
    return Resposta.ok(auditoria.listar(entidade, registroId, p.getPageNumber(), p.getPageSize()));
  }
}
