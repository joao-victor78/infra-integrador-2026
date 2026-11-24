package br.com.erp.financeiro.relatorios;

import br.com.erp.financeiro.api.ApiException;
import br.com.erp.financeiro.cobrancas.Cobranca;
import br.com.erp.financeiro.cobrancas.CobrancaRepository;
import br.com.erp.financeiro.cobrancas.CobrancaService;
import br.com.erp.financeiro.cobrancas.StatusCobranca;
import br.com.erp.financeiro.comum.Dinheiro;
import br.com.erp.financeiro.comum.Relogio;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação das contas a receber (RF-11). A resposta é o próprio arquivo — única exceção ao envelope
 * (RN-57); erros (400, 401, 403) continuam no envelope. Respeita o tenant e os filtros (RN-58).
 */
@Tag(name = "contas-a-receber")
@RestController
public class RelatorioController {

  static final int LIMITE = 10_000;
  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final List<String> CABECALHO = List.of("Cobrança", "Empresa (id CRM)", "Competência", "Vencimento",
      "Descrição", "Valor", "Desconto", "Juros", "Multa", "Pago", "Saldo", "Forma", "Status");

  private final CobrancaRepository cobrancas;
  private final Relogio relogio;

  public RelatorioController(CobrancaRepository cobrancas, Relogio relogio) {
    this.cobrancas = cobrancas;
    this.relogio = relogio;
  }

  @GetMapping("/api/financeiro/relatorios/cobrancas/exportar")
  @PreAuthorize("hasAuthority('financeiro.relatorio.exportar')")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> exportar(
      @RequestParam String formato,
      @RequestParam(required = false) LocalDate vencimentoInicio,
      @RequestParam(required = false) LocalDate vencimentoFim,
      @RequestParam(required = false) StatusCobranca status,
      @RequestParam(required = false) UUID clienteId) {
    String f = formato.toLowerCase();
    if (!List.of("pdf", "xlsx", "csv").contains(f)) throw ApiException.campo("formato", "FORMATO_INVALIDO", "Use pdf, xlsx ou csv.");
    List<Cobranca> lista = cobrancas.findAll(
        CobrancaService.especificacao(new CobrancaService.Filtro(clienteId, status, vencimentoInicio, vencimentoFim, null)),
        PageRequest.of(0, LIMITE, Sort.by("dataVencimento").and(Sort.by("id")))).getContent();
    List<List<Object>> linhas = new ArrayList<>();
    for (Cobranca c : lista) {
      List<Object> l = new ArrayList<>();
      l.add(c.getId().toString());
      l.add(c.getEmpresaId().toString());
      l.add(c.getCompetencia());
      l.add(c.getDataVencimento().format(DATA));
      l.add(c.getDescricao());
      l.add(c.getValor());
      l.add(c.getDesconto());
      l.add(c.getJuros());
      l.add(c.getMulta());
      l.add(c.getValorPago());
      l.add(c.saldo());
      l.add(c.getFormaPagamento() == null ? null : c.getFormaPagamento().name());
      l.add(c.getStatus().name());
      linhas.add(l);
    }
    String nome = "cobrancas-" + relogio.hoje() + "." + f;
    byte[] corpo;
    MediaType tipo;
    switch (f) {
      case "csv" -> {
        corpo = Planilhas.csv(CABECALHO, linhas);
        tipo = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
      }
      case "xlsx" -> {
        corpo = Planilhas.xlsx("Cobranças", CABECALHO, linhas);
        tipo = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      }
      default -> {
        corpo = Planilhas.pdf("Contas a receber — " + relogio.hoje().format(DATA) + " — " + lista.size() + " cobrança(s)",
            linhasPdf(lista));
        tipo = MediaType.APPLICATION_PDF;
      }
    }
    return ResponseEntity.ok()
        .contentType(tipo)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
        .body(corpo);
  }

  private static List<String> linhasPdf(List<Cobranca> lista) {
    List<String> l = new ArrayList<>();
    String formato = "%-9s %-9s %-7s %-10s %-34s %14s %14s %14s %-9s";
    l.add(String.format(formato, "Cobrança", "Empresa", "Compet.", "Vencimento", "Descrição", "Valor", "Pago", "Saldo", "Status"));
    l.add("-".repeat(130));
    java.math.BigDecimal total = Dinheiro.ZERO;
    java.math.BigDecimal saldo = Dinheiro.ZERO;
    for (Cobranca c : lista) {
      String desc = c.getDescricao() == null ? "" : c.getDescricao();
      l.add(String.format(formato, c.getId().toString().substring(0, 8), c.getEmpresaId().toString().substring(0, 8),
          c.getCompetencia(), c.getDataVencimento().format(DATA), desc.length() > 34 ? desc.substring(0, 33) + "…" : desc,
          Dinheiro.brl(c.totalDevido()), Dinheiro.brl(c.getValorPago()), Dinheiro.brl(c.saldo()), c.getStatus()));
      total = total.add(c.totalDevido());
      saldo = saldo.add(c.saldo());
    }
    l.add("-".repeat(130));
    l.add(String.format("%-73s %14s %14s %14s", "Total", Dinheiro.brl(total), "", Dinheiro.brl(saldo)));
    return l;
  }
}
