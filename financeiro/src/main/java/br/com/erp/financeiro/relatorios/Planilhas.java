package br.com.erp.financeiro.relatorios;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Geradores mínimos de CSV, XLSX (Office Open XML) e PDF para a exportação (RF-11), sem bibliotecas
 * externas. Cada linha é uma lista de células: String, BigDecimal ou null.
 */
final class Planilhas {

  private Planilhas() {}

  // ------------------------------------------------------------------ CSV (Excel pt-BR: ";" e BOM UTF-8)

  static byte[] csv(List<String> cabecalho, List<List<Object>> linhas) {
    StringBuilder sb = new StringBuilder("﻿");
    sb.append(String.join(";", cabecalho.stream().map(Planilhas::celulaCsv).toList())).append("\r\n");
    for (List<Object> l : linhas) {
      sb.append(String.join(";", l.stream().map(Planilhas::celulaCsv).toList())).append("\r\n");
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String celulaCsv(Object v) {
    if (v == null) return "";
    String s = v instanceof BigDecimal d ? d.toPlainString().replace('.', ',') : v.toString();
    // Evita injeção de fórmula ao abrir no Excel (CSV injection).
    if (!(v instanceof BigDecimal) && !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
  }

  // ------------------------------------------------------------------ XLSX

  static byte[] xlsx(String aba, List<String> cabecalho, List<List<Object>> linhas) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      entrada(zip, "[Content_Types].xml", """
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          </Types>""");
      entrada(zip, "_rels/.rels", """
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
          </Relationships>""");
      entrada(zip, "xl/workbook.xml", """
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
          </workbook>""".formatted(xml(aba)));
      entrada(zip, "xl/_rels/workbook.xml.rels", """
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
          </Relationships>""");
      // Estilo 1: negrito (cabeçalho); estilo 2: número com 2 casas (#,##0.00).
      entrada(zip, "xl/styles.xml", """
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
          <borders count="1"><border/></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
          </styleSheet>""");
      StringBuilder s = new StringBuilder("""
          <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
          <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""");
      List<Object> cab = new ArrayList<>(cabecalho);
      linha(s, 1, cab, true);
      for (int i = 0; i < linhas.size(); i++) linha(s, i + 2, linhas.get(i), false);
      s.append("</sheetData></worksheet>");
      entrada(zip, "xl/worksheets/sheet1.xml", s.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static void linha(StringBuilder s, int numero, List<Object> celulas, boolean cabecalho) {
    s.append("<row r=\"").append(numero).append("\">");
    for (int c = 0; c < celulas.size(); c++) {
      Object v = celulas.get(c);
      String ref = coluna(c) + numero;
      if (v == null) continue;
      if (v instanceof BigDecimal d) {
        s.append("<c r=\"").append(ref).append("\" s=\"2\"><v>").append(d.toPlainString()).append("</v></c>");
      } else {
        s.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(cabecalho ? " s=\"1\"" : "")
            .append("><is><t>").append(xml(v.toString())).append("</t></is></c>");
      }
    }
    s.append("</row>");
  }

  private static String coluna(int indice) {
    StringBuilder sb = new StringBuilder();
    for (int n = indice + 1; n > 0; n = (n - 1) / 26) sb.insert(0, (char) ('A' + (n - 1) % 26));
    return sb.toString();
  }

  private static String xml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
  }

  private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
    zip.putNextEntry(new ZipEntry(nome));
    zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  // ------------------------------------------------------------------ PDF (texto em Courier, A4 paisagem)

  private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
  private static final int LINHAS_POR_PAGINA = 48;

  static byte[] pdf(String titulo, List<String> linhasDeTexto) {
    List<List<String>> paginas = new ArrayList<>();
    for (int i = 0; i < Math.max(1, linhasDeTexto.size()); i += LINHAS_POR_PAGINA) {
      paginas.add(linhasDeTexto.subList(Math.min(i, linhasDeTexto.size()), Math.min(i + LINHAS_POR_PAGINA, linhasDeTexto.size())));
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<Integer> deslocamentos = new ArrayList<>();
    escrever(out, "%PDF-1.4\n%âãÏÓ\n");
    int n = paginas.size();
    // objetos: 1 catálogo, 2 páginas, 3 fonte, depois (página, conteúdo) para cada página
    objeto(out, deslocamentos, 1, "<< /Type /Catalog /Pages 2 0 R >>");
    StringBuilder kids = new StringBuilder();
    for (int p = 0; p < n; p++) kids.append(4 + p * 2).append(" 0 R ");
    objeto(out, deslocamentos, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
    objeto(out, deslocamentos, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");
    for (int p = 0; p < n; p++) {
      int pagina = 4 + p * 2;
      StringBuilder c = new StringBuilder("BT /F1 12 Tf 36 560 Td (").append(pdfTexto(titulo))
          .append(") Tj /F1 8 Tf 0 -22 Td 10 TL");
      for (String l : paginas.get(p)) c.append(" (").append(pdfTexto(l)).append(") '");
      c.append(" ET BT /F1 7 Tf 36 20 Td (").append(pdfTexto("Página " + (p + 1) + " de " + n)).append(") Tj ET");
      byte[] conteudo = c.toString().getBytes(LATIN1);
      objeto(out, deslocamentos, pagina, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents "
          + (pagina + 1) + " 0 R >>");
      deslocamentos.add(out.size());
      escrever(out, (pagina + 1) + " 0 obj\n<< /Length " + conteudo.length + " >>\nstream\n");
      out.writeBytes(conteudo);
      escrever(out, "\nendstream\nendobj\n");
    }
    int xref = out.size();
    StringBuilder x = new StringBuilder("xref\n0 ").append(deslocamentos.size() + 1).append("\n0000000000 65535 f \n");
    for (int d : deslocamentos) x.append(String.format("%010d 00000 n \n", d));
    x.append("trailer\n<< /Size ").append(deslocamentos.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    escrever(out, x.toString());
    return out.toByteArray();
  }

  private static void objeto(ByteArrayOutputStream out, List<Integer> deslocamentos, int numero, String corpo) {
    deslocamentos.add(out.size());
    escrever(out, numero + " 0 obj\n" + corpo + "\nendobj\n");
  }

  private static void escrever(ByteArrayOutputStream out, String s) {
    out.writeBytes(s.getBytes(LATIN1));
  }

  /** Escapa parênteses e barra; troca o que o Latin-1 não tem por "?". */
  private static String pdfTexto(String s) {
    StringBuilder sb = new StringBuilder();
    for (char ch : s.toCharArray()) {
      if (ch == '(' || ch == ')' || ch == '\\') sb.append('\\').append(ch);
      else if (ch < 32) sb.append(' ');
      else if (ch > 255) sb.append('?');
      else sb.append(ch);
    }
    return sb.toString();
  }
}
