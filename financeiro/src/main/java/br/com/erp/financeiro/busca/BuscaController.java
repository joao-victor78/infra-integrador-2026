package br.com.erp.financeiro.busca;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.api.Resposta;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.contaspagar.ContaPagar;
import br.com.erp.financeiro.contaspagar.ContaPagarRepository;
import br.com.erp.financeiro.seguranca.Usuarios;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca global da casca (RF-09, Contrato §8.6): até 5 resultados (RN-50), cada tipo só para quem pode
 * ler aquele recurso (RN-51) — o subtítulo mostra valor, então a permissão fraca não basta.
 */
@Tag(name = "busca")
@RestController
public class BuscaController {

  static final int MAXIMO = 5;
  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CobrancaRepository cobrancas;
  private final ContaPagarRepository contas;

  public BuscaController(CobrancaRepository cobrancas, ContaPagarRepository contas) {
    this.cobrancas = cobrancas;
    this.contas = contas;
  }

  public record ResultadoBusca(UUID id, String titulo, String subtitulo, String rota) {}

  @GetMapping("/api/financeiro/busca")
  @PreAuthorize("hasAuthority('financeiro.acessar')")
  @Transactional(readOnly = true)
  public Resposta<List<ResultadoBusca>> buscar(@RequestParam String q) {
    String termo = q == null ? "" : q.trim();
    if (termo.length() < 2) throw ApiException.campo("q", "TERMO_CURTO", "Informe ao menos 2 caracteres."); // RN-49
    String like = "%" + termo.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    String prefixo = termo.toLowerCase(Locale.ROOT).replace("#", "") + "%";
    List<ResultadoBusca> resultado = new ArrayList<>();
    if (Usuarios.tem("financeiro.contareceber.ver")) {
      for (Cobranca c : cobrancas.buscar(like, prefixo, PageRequest.of(0, MAXIMO))) {
        resultado.add(new ResultadoBusca(c.getId(),
            "Cobrança #" + c.getId().toString().substring(0, 8) + (c.getDescricao() != null ? " — " + c.getDescricao() : ""),
            "Vence em " + c.getDataVencimento().format(DATA) + " · " + Dinheiro.brl(c.totalDevido()) + " · " + c.getStatus(),
            "/cobrancas/" + c.getId()));
      }
    }
    if (Usuarios.tem("financeiro.contapagar.ver") && resultado.size() < MAXIMO) {
      for (ContaPagar c : contas.buscar(like, prefixo, PageRequest.of(0, MAXIMO - resultado.size()))) {
        resultado.add(new ResultadoBusca(c.getId(),
            "Conta a pagar — " + c.getFornecedorNome() + (c.getDocumento() != null ? " (" + c.getDocumento() + ")" : ""),
            "Vence em " + c.getDataVencimento().format(DATA) + " · " + Dinheiro.brl(c.getValor()) + " · " + c.getStatus(),
            "/contas-pagar/" + c.getId()));
      }
    }
    return Resposta.ok(resultado.size() > MAXIMO ? resultado.subList(0, MAXIMO) : resultado);
  }
}
