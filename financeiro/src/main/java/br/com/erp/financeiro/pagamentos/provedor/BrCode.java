package br.com.erp.financeiro.pagamentos.provedor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.UUID;

/**
 * Geração do "Pix Copia e Cola" (BR Code, padrão EMV-MPM do Manual de Padrões para Iniciação do Pix — BCB).
 * Nesta versão o payload é gerado localmente (QR estático com txid); a integração com a API Pix de um PSP
 * para QR dinâmico fica como trabalho futuro.
 */
public final class BrCode {

  private BrCode() {}

  private static String campo(String id, String valor) {
    return id + String.format("%02d", valor.length()) + valor;
  }

  /** CRC16-CCITT (polinômio 0x1021, valor inicial 0xFFFF), exigido pelo campo 63 do BR Code. */
  public static String crc16(String payload) {
    int crc = 0xFFFF;
    for (int i = 0; i < payload.length(); i++) {
      crc ^= payload.charAt(i) << 8;
      for (int j = 0; j < 8; j++) crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
    }
    return String.format("%04X", crc);
  }

  /** 32 caracteres alfanuméricos (limite do Pix: 26 a 35). */
  public static String gerarTxid() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /** Remove acentos e símbolos e limita o tamanho (nome até 25, cidade até 15). */
  static String ascii(String s, int max) {
    String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9 ]", "").toUpperCase();
    return t.length() > max ? t.substring(0, max) : t;
  }

  public static String gerar(String chave, BigDecimal valor, String txid, String nome, String cidade) {
    String conta = campo("00", "br.gov.bcb.pix") + campo("01", chave);
    String semCrc = campo("00", "01")
        + campo("26", conta)
        + campo("52", "0000")
        + campo("53", "986")
        + campo("54", valor.setScale(2, RoundingMode.HALF_UP).toPlainString())
        + campo("58", "BR")
        + campo("59", ascii(nome == null ? "EMPRESA" : nome, 25))
        + campo("60", ascii(cidade == null ? "GOIANIA" : cidade, 15))
        + campo("62", campo("05", txid.length() > 25 ? txid.substring(0, 25) : txid))
        + "6304";
    return semCrc + crc16(semCrc);
  }
}
