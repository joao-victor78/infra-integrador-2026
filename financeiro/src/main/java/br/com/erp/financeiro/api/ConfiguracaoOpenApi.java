package br.com.erp.financeiro.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI em /api/financeiro/docs, gerado do código. O contrato oficial, revisado pelo Grupo 2, é
 * infra-integrador-2026/contratos/financeiro.yaml (RNF-07): a resposta real tem de ser igual a ele.
 */
@Configuration
public class ConfiguracaoOpenApi {

  @Bean
  OpenAPI documentacao() {
    return new OpenAPI()
        .info(new Info().title("Financeiro — Módulo (Grupo 8)").version("1.0.0")
            .description("Contas a receber e a pagar, recorrência, pagamentos, webhook, régua, inadimplência, MRR, "
                + "busca, status financeiro, exportação e auditoria. EOR v1.0 · Contrato de Integração."))
        .components(new Components().addSecuritySchemes("bearerJwt",
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerJwt"));
  }
}
