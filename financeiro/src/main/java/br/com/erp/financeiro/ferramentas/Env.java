package br.com.erp.financeiro.ferramentas;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Leitura do .env para as ferramentas de linha de comando (variáveis de ambiente têm prioridade). */
final class Env {

  private static final Properties ARQUIVO = new Properties();

  static {
    Path p = Path.of(System.getenv().getOrDefault("ENV_FILE", ".env"));
    if (Files.exists(p)) {
      try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
        ARQUIVO.load(r);
      } catch (IOException ignorado) {
        // sem .env: usa os padrões
      }
    }
  }

  private Env() {}

  static String get(String nome, String padrao) {
    String v = System.getenv(nome);
    if (v == null || v.isBlank()) v = ARQUIVO.getProperty(nome);
    return v == null || v.isBlank() ? padrao : v.trim();
  }
}
