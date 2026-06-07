package br.com.finflow.dashboard.controller

import br.com.finflow.dashboard.dto.DashboardSummaryDto
import br.com.finflow.dashboard.service.DashboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/dashboard")
@Tag(
    name = "Dashboard",
    description = """
        Resumo financeiro consolidado para o dashboard principal.

        **Cache Redis de 5 minutos**: evita recálculo em consultas repetidas.
        Alinhado com o `staleTime` do TanStack Query no frontend — após 5 min
        o frontend revalida automaticamente em background.

        **Performance**: meta de < 2 segundos (atendida via cache).
        Primeira consulta do período pode levar até 500ms (sem cache).
    """
)
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping("/summary")
    @Operation(
        summary = "Resumo financeiro do período",
        description = """
            Retorna o consolidado financeiro do período informado:
            - **totalIncome**: soma de todas as receitas (INCOME)
            - **totalExpenses**: soma de todas as despesas (EXPENSE)
            - **netBalance**: totalIncome − totalExpenses
            - **byCategory**: breakdown de despesas por categoria (para gráfico pizza no frontend)
            - **monthlyTrend**: evolução mensal dos últimos 6 meses (para gráfico linha)

            **Exemplo real** — dados do extrato Itaú dez/2025:
            - Receitas: R$ 15.793,17 (salário R$ 11.474,71 + 13º R$ 4.318,46)
            - Despesas: R$ 17.076,38 (fatura Itaú Black R$ 14.747,25 + Eletropaulo R$ 236,42 + outros)
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Resumo financeiro calculado",
            content = [Content(
                mediaType = "application/json",
                examples = [
                    ExampleObject(
                        name = "Dezembro/2025 — dados reais extrato Itaú",
                        summary = "Mês com 13º salário e fatura Itaú Black",
                        value = """{
  "period": {
    "start": "2025-12-01",
    "end": "2025-12-31"
  },
  "totalIncome": 15793.17,
  "totalExpenses": 17076.38,
  "netBalance": -1283.21,
  "byCategory": [
    {
      "categoryId": "uuid-outros",
      "categoryName": "Outros",
      "total": 14747.25,
      "percentage": 86.37,
      "color": "#6B7280"
    },
    {
      "categoryId": "uuid-moradia",
      "categoryName": "Moradia",
      "total": 236.42,
      "percentage": 1.38,
      "color": "#3B82F6"
    },
    {
      "categoryId": "uuid-saude",
      "categoryName": "Saúde",
      "total": 1776.00,
      "percentage": 10.40,
      "color": "#EC4899"
    }
  ],
  "monthlyTrend": [
    { "month": "2025-07", "income": 11474.71, "expenses": 9800.00 },
    { "month": "2025-08", "income": 11474.71, "expenses": 10200.00 },
    { "month": "2025-09", "income": 11474.71, "expenses": 11600.00 },
    { "month": "2025-10", "income": 11474.71, "expenses": 12100.00 },
    { "month": "2025-11", "income": 12416.91, "expenses": 11617.38 },
    { "month": "2025-12", "income": 15793.17, "expenses": 17076.38 }
  ]
}"""
                    ),
                    ExampleObject(
                        name = "Mês positivo (receitas > despesas)",
                        summary = "Mês típico sem gastos extras",
                        value = """{
  "period": { "start": "2025-11-01", "end": "2025-11-30" },
  "totalIncome": 12416.91,
  "totalExpenses": 11617.38,
  "netBalance": 799.53,
  "byCategory": [
    { "categoryId": "uuid-outros", "categoryName": "Outros", "total": 11617.38, "percentage": 100.0, "color": "#6B7280" }
  ],
  "monthlyTrend": []
}"""
                    )
                ]
            )]
        ),
        ApiResponse(responseCode = "400", description = "Datas inválidas ou ausentes"),
        ApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado")
    )
    fun summary(
        @Parameter(description = "Data início do período (ISO 8601)", example = "2025-12-01", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) start: LocalDate,
        @Parameter(description = "Data fim do período (ISO 8601)", example = "2025-12-31", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) end: LocalDate,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<DashboardSummaryDto> {
        val userId = UUID.fromString(principal.username)
        return ResponseEntity.ok(dashboardService.getSummary(userId, start, end))
    }
}
