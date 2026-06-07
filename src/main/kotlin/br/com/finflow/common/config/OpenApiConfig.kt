package br.com.finflow.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("FinFlow API")
                .description("""
                    API REST do FinFlow — app de organização financeira pessoal.

                    ## Autenticação
                    Todos os endpoints (exceto `/api/auth/**`) exigem JWT no header:
                    ```
                    Authorization: Bearer <access_token>
                    ```
                    O token é obtido após login social (Google/Facebook) e tem validade de **15 minutos**.
                    Use `POST /api/auth/refresh` para renovar sem novo login.

                    ## Fluxo principal
                    1. Login via OAuth2 → recebe `access_token` (JWT) + cookie `refresh_token`
                    2. Upload de extrato PDF ou planilha XLSX → transações extraídas automaticamente
                    3. Categorização automática (regras + IA via Claude Haiku)
                    4. Consulta dashboard consolidado com gráficos
                    5. Upload de extrato de investimentos → portfólio consolidado

                    ## Formatos suportados
                    - **PDF**: extratos e faturas Itaú, Nubank, Bradesco, Santander
                    - **XLSX**: planilhas Itaú Ion, XP, Rico, BTG
                    - **Limite**: 10MB por arquivo
                """.trimIndent())
                .version("1.0.0")
                .contact(Contact().name("FinFlow").email("dev@finflow.com.br"))
        )
        .addServersItem(Server().url("http://localhost:8080").description("Desenvolvimento local"))
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT gerado após login OAuth2. Válido por 15 minutos.")
            )
        )
}
