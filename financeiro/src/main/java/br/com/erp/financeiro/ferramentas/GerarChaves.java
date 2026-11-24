package br.com.erp.financeiro.ferramentas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * DESENVOLVIMENTO: gera um par RSA simulando o módulo de Autenticação.
 * Em produção, o Financeiro recebe SOMENTE a chave pública; a privada fica no módulo de Auth.
 *
 * <p>Uso: {@code mvn -q compile exec:java@chaves} (acrescente {@code -Dexec.args=--force} para recriar)
 */
public final class GerarChaves {

  private GerarChaves() {}

  static String pem(String tipo, byte[] der) {
    String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
    return "-----BEGIN " + tipo + "-----\n" + b64 + "\n-----END " + tipo + "-----\n";
  }

  public static void main(String[] args) throws Exception {
    Path dir = Path.of("keys");
    Files.createDirectories(dir);
    Path privada = dir.resolve("auth-private.pem");
    if (Files.exists(privada) && (args.length == 0 || !args[0].equals("--force"))) {
      System.out.println("keys/ já existe (use -Dexec.args=--force para recriar)");
      return;
    }
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair par = g.generateKeyPair();
    Files.writeString(dir.resolve("auth-public.pem"), pem("PUBLIC KEY", par.getPublic().getEncoded())); // SPKI
    Files.writeString(privada, pem("PRIVATE KEY", par.getPrivate().getEncoded())); // PKCS#8
    try {
      Files.setPosixFilePermissions(privada, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignorado) {
      // Windows
    }
    System.out.println("Par RS256 gerado em keys/ (auth-public.pem / auth-private.pem)");
  }
}
