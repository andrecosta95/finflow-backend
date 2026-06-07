package br.com.finflow.investment.controller

import br.com.finflow.investment.service.InvestmentService
import br.com.finflow.investment.service.PortfolioSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/investments")
@Tag(
    name = "Investimentos",
    description = """
        Portfólio consolidado de investimentos multi-banco.

        **Bancos suportados (parsers disponíveis):**
        - Itaú Ion: extrato PDF e planilha XLSX (exportada pelo app)
        - Novos bancos: adicionar nova classe implementando `InvestmentParser` (Strategy Pattern)

        **Importar extrato**: faça upload via `POST /api/documents/upload`.
        O sistema detecta automaticamente se é um extrato de transações ou de investimentos
        pelo conteúdo do arquivo.

        **Dados reais do XLSX Itaú Ion** (investimento_ion_itau.xlsx):
        - Total carteira: R$ 233.987,35
        - Rentabilidade 2026 (até jun): +5,48% (+R$ 10.801,21)
        - Maior posição: PRIVILEGE RF REF DI — R$ 110.222,20 (+5,76% no ano)
    """
)
class InvestmentController(private val investmentService: InvestmentService) {

    @GetMapping("/portfolio")
    @Operation(
        summary = "Portfólio consolidado de investimentos",
        description = """
            Retorna a carteira completa do usuário agregada de todos os bancos:
            - **totalBalance**: soma de todos os saldos atuais
            - **totalInvested**: soma dos valores aplicados originalmente
            - **globalProfitabilityPct**: rentabilidade total da carteira em percentual
            - **products**: lista detalhada de cada produto (CDB, FI, PGBL, etc.)
            - **byBank**: alocação total por banco (para gráfico de barra)
            - **byType**: alocação por tipo de produto (para gráfico pizza)

            **Exemplo baseado no XLSX real do Itaú Ion** carregado no upload:
            Carteira com 5 produtos Itaú — total R$ 233.987,35, rentabilidade +5,48% em 2026.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Portfólio calculado com sucesso",
            content = [Content(
                mediaType = "application/json",
                examples = [
                    ExampleObject(
                        name = "Portfólio Itaú — dados reais jun/2026",
                        summary = "Extraído do arquivo investimento_ion_itau.xlsx",
                        value = """{
  "totalBalance": 233987.35,
  "totalInvested": 223186.14,
  "globalProfitabilityPct": 4.84,
  "products": [
    {
      "id": "d1e2f3a4-b5c6-7890-defa-b12345678910",
      "bank": "Itaú",
      "productName": "PRIVILEGE RF REF DI",
      "productType": "FI",
      "currentBalance": 110222.20,
      "investedAmount": null,
      "profitabilityPct": 5.76,
      "referenceDate": "2026-06-03",
      "maturityDate": null
    },
    {
      "id": "e2f3a4b5-c6d7-8901-efab-c23456789011",
      "bank": "Itaú",
      "productName": "CDB BANCO BMG PRE 13.88% 27/02/2029",
      "productType": "CDB",
      "currentBalance": 20692.54,
      "investedAmount": 20021.34,
      "profitabilityPct": 3.35,
      "referenceDate": "2026-06-03",
      "maturityDate": "2029-02-27"
    },
    {
      "id": "f3a4b5c6-d7e8-9012-fabc-d34567890012",
      "bank": "Itaú",
      "productName": "PGBL ITUBERS RF",
      "productType": "PGBL",
      "currentBalance": 22572.84,
      "investedAmount": null,
      "profitabilityPct": null,
      "referenceDate": "2026-06-03",
      "maturityDate": null
    },
    {
      "id": "a4b5c6d7-e8f9-0123-abcd-e45678901013",
      "bank": "Itaú",
      "productName": "ITUBERS GDPLUS",
      "productType": "FI",
      "currentBalance": 16299.57,
      "investedAmount": null,
      "profitabilityPct": 5.47,
      "referenceDate": "2026-06-03",
      "maturityDate": null
    },
    {
      "id": "b5c6d7e8-f9a0-1234-bcde-f56789012014",
      "bank": "Itaú",
      "productName": "ITAÚ GD BAIXA VOL MM",
      "productType": "FI",
      "currentBalance": 6132.83,
      "investedAmount": null,
      "profitabilityPct": 2.67,
      "referenceDate": "2026-06-03",
      "maturityDate": null
    }
  ],
  "byBank": [
    { "bank": "Itaú", "total": 233987.35 }
  ],
  "byType": [
    { "type": "FI",   "total": 132654.60 },
    { "type": "CDB",  "total": 57626.47 },
    { "type": "PGBL", "total": 43706.28 }
  ]
}"""
                    )
                ]
            )]
        ),
        ApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado")
    )
    fun portfolio(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<PortfolioSummary> {
        val userId = UUID.fromString(principal.username)
        return ResponseEntity.ok(investmentService.getPortfolio(userId))
    }
}
