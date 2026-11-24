package br.com.erp.financeiro.pagamentos.provedor;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Imagem do QR Code (PNG ou SVG) a partir do texto do Pix copia e cola. */
public final class QrCode {

  private static final int MARGEM = 2;

  private QrCode() {}

  private static BitMatrix matriz(String texto, int tamanho) {
    try {
      return new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamanho, tamanho,
          // Sem CHARACTER_SET: o BR Code é ASCII e um marcador ECI confundiria alguns apps de banco.
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, MARGEM));
    } catch (WriterException e) {
      throw new IllegalArgumentException("Texto grande demais para um QR Code", e);
    }
  }

  public static byte[] png(String texto, int largura) {
    try (var out = new ByteArrayOutputStream()) {
      MatrixToImageWriter.writeToStream(matriz(texto, largura), "PNG", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** SVG vetorial: um quadrado por módulo, em uma única path. */
  public static String svg(String texto) {
    BitMatrix m = matriz(texto, 0);
    int n = m.getWidth();
    StringBuilder d = new StringBuilder();
    for (int y = 0; y < n; y++) {
      for (int x = 0; x < n; x++) {
        if (m.get(x, y)) d.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
      }
    }
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + n + " " + n + "\" shape-rendering=\"crispEdges\">"
        + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/><path fill=\"#000000\" d=\"" + d + "\"/></svg>";
  }

  /** Versão para terminal: dois módulos por caractere (meio-bloco Unicode). */
  public static String terminal(String texto) {
    BitMatrix m = matriz(texto, 0);
    StringBuilder sb = new StringBuilder();
    for (int y = 0; y < m.getHeight(); y += 2) {
      for (int x = 0; x < m.getWidth(); x++) {
        boolean cima = m.get(x, y);
        boolean baixo = y + 1 < m.getHeight() && m.get(x, y + 1);
        sb.append(cima && baixo ? '█' : cima ? '▀' : baixo ? '▄' : ' ');
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
