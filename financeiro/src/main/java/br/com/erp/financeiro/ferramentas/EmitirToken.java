package br.com.erp.financeiro.ferramentas;

import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DESENVOLVIMENTO, sem o identity no ar: emite um JWT RS256 no formato do identity (Contrato §4.2) com a
 * chave de keys/auth-private.pem. A API aceita esse token quando JWT_PUBLIC_KEY_PATH aponta para a chave
 * pública correspondente. Com o identity no ar, use um token real (docs/usuarios-de-teste.md do infra).
 *
 * <p>Uso: {@code mvn -q compile exec:java@token -Dexec.args="[tenant_uuid] [perfil] [sub_uuid]"}
 * — perfis: ADMINISTRADOR (padrão), GESTOR, FINANCEIRO, CONTABILIDADE, VENDEDOR.
 */
public final class EmitirToken {

  /** Perfis padrão do catálogo de permissões (EOR §6; permissoes/financeiro.yaml). */
  public static final Map<String, List<String>> PERFIS = Map.of(
      "ADMINISTRADOR", List.of("financeiro.acessar", "financeiro.cobranca.ver_resumo", "financeiro.contareceber.ver",
          "financeiro.contareceber.criar", "financeiro.contareceber.editar", "financeiro.contareceber.excluir",
          "financeiro.contapagar.ver", "financeiro.contapagar.criar", "financeiro.contapagar.aprovar",
          "financeiro.recorrencia.ver", "financeiro.recorrencia.administrar", "financeiro.regua.ver",
          "financeiro.regua.administrar", "financeiro.inadimplencia.ver", "financeiro.dashboard.ver",
          "financeiro.relatorio.exportar", "financeiro.auditoria.ver"),
      "GESTOR", List.of("financeiro.acessar", "financeiro.cobranca.ver_resumo", "financeiro.contareceber.ver",
          "financeiro.contapagar.ver", "financeiro.contapagar.aprovar", "financeiro.recorrencia.ver",
          "financeiro.regua.ver", "financeiro.inadimplencia.ver", "financeiro.dashboard.ver", "financeiro.relatorio.exportar"),
      "FINANCEIRO", List.of("financeiro.acessar", "financeiro.cobranca.ver_resumo", "financeiro.contareceber.ver",
          "financeiro.contareceber.criar", "financeiro.contareceber.editar", "financeiro.contareceber.excluir",
          "financeiro.contapagar.ver", "financeiro.contapagar.criar", "financeiro.recorrencia.ver",
          "financeiro.recorrencia.administrar", "financeiro.regua.ver", "financeiro.regua.administrar",
          "financeiro.inadimplencia.ver", "financeiro.dashboard.ver", "financeiro.relatorio.exportar"),
      "CONTABILIDADE", List.of("financeiro.acessar", "financeiro.cobranca.ver_resumo", "financeiro.relatorio.exportar"),
      "VENDEDOR", List.of("financeiro.cobranca.ver_resumo"));

  private EmitirToken() {}

  public static PrivateKey lerChavePrivada(String pem) throws Exception {
    String b64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
  }

  public static String emitir(PrivateKey chave, String tenant, String sub, String perfil, Duration validade) {
    Instant agora = Instant.now();
    return Jwts.builder()
        .issuer("http://identity:8081")
        .audience().add("plataforma").and()
        .subject(sub)
        .claim("tenant_id", tenant)
        .claim("nome", "Usuário de desenvolvimento (" + perfil + ")")
        .claim("roles", List.of(perfil))
        .claim("perms", PERFIS.getOrDefault(perfil, PERFIS.get("ADMINISTRADOR")))
        .claim("equipes", List.of())
        .issuedAt(Date.from(agora))
        .expiration(Date.from(agora.plus(validade)))
        .signWith(chave, Jwts.SIG.RS256)
        .compact();
  }

  public static void main(String[] args) throws Exception {
    String tenant = args.length > 0 ? args[0] : UUID.randomUUID().toString();
    String perfil = args.length > 1 ? args[1].toUpperCase() : "ADMINISTRADOR";
    String sub = args.length > 2 ? args[2] : UUID.randomUUID().toString();
    PrivateKey chave = lerChavePrivada(Files.readString(Path.of(Env.get("JWT_PRIVATE_KEY_PATH", "keys/auth-private.pem"))));
    System.out.println(emitir(chave, tenant, sub, perfil, Duration.ofHours(8)));
  }
}
