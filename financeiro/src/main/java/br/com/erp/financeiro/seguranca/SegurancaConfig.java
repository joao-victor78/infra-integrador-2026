package br.com.erp.financeiro.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * O módulo só valida tokens; quem emite é o identity (Contrato §4, EOR §2.3). Chave pública pelo
 * JWKS; em desenvolvimento sem o identity no ar, por um PEM local (JWT_PUBLIC_KEY_PATH).
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

  @Bean
  SecurityFilterChain filtrosDeSeguranca(HttpSecurity http, ObjectMapper mapper) throws Exception {
    EscritorDeErro erros = new EscritorDeErro(mapper);
    AuthenticationEntryPoint naoAutenticado = (req, res, ex) -> erros.escrever(res, 401, "Autenticação necessária.");
    AccessDeniedHandler semPermissao = (req, res, ex) -> erros.escrever(res, 403, "Sem permissão para esta operação.");

    http
        .csrf(csrf -> csrf.disable()) // token no cabeçalho, sem cookie: CSRF não se aplica
        .cors(cors -> cors.disable()) // o navegador fala só com o gateway, na mesma origem (§3)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(regras -> regras
            .requestMatchers("/api/financeiro/health").permitAll()
            .requestMatchers("/public/**").permitAll() // só o webhook; protegido por assinatura (RF-05)
            .requestMatchers("/api/financeiro/docs/**", "/api/financeiro/docs", "/api/financeiro/docs-json/**",
                "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(servidor -> servidor
            .jwt(jwt -> jwt.jwtAuthenticationConverter(permissoesDoToken()))
            .authenticationEntryPoint(naoAutenticado)
            .accessDeniedHandler(semPermissao))
        .exceptionHandling(e -> e.authenticationEntryPoint(naoAutenticado).accessDeniedHandler(semPermissao))
        .addFilterAfter(new TenantFiltro(erros), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /** Cada item do claim "perms" vira uma authority, sem prefixo: hasAuthority('financeiro.contareceber.ver'). */
  static JwtAuthenticationConverter permissoesDoToken() {
    JwtGrantedAuthoritiesConverter permissoes = new JwtGrantedAuthoritiesConverter();
    permissoes.setAuthoritiesClaimName("perms");
    permissoes.setAuthorityPrefix("");
    JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
    conversor.setJwtGrantedAuthoritiesConverter(permissoes);
    return conversor;
  }

  @Bean
  JwtDecoder jwtDecoder(@Value("${financeiro.jwt.jwks-uri}") String jwksUri,
      @Value("${financeiro.jwt.chave-publica-arquivo:}") String arquivoChave,
      @Value("${financeiro.jwt.audiencia:plataforma}") String audiencia) {
    NimbusJwtDecoder decoder = arquivoChave == null || arquivoChave.isBlank()
        ? NimbusJwtDecoder.withJwkSetUri(jwksUri).jwsAlgorithm(SignatureAlgorithm.RS256).build()
        : NimbusJwtDecoder.withPublicKey(lerChavePublica(Path.of(arquivoChave))).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    OAuth2TokenValidator<Jwt> audienciaValida = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audiencia)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência do token inválida", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienciaValida));
    return decoder;
  }

  static RSAPublicKey lerChavePublica(Path arquivo) {
    try {
      String pem = Files.readString(arquivo).replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
      return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    } catch (IOException e) {
      throw new IllegalStateException("Não foi possível ler a chave pública em " + arquivo, e);
    } catch (Exception e) {
      throw new IllegalStateException("Chave pública inválida em " + arquivo + " (esperado PEM 'BEGIN PUBLIC KEY')", e);
    }
  }
}
