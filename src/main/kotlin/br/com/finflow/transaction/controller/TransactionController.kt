package br.com.finflow.transaction.controller

import br.com.finflow.transaction.dto.TransactionDto
import br.com.finflow.transaction.dto.UpdateCategoryRequest
import br.com.finflow.transaction.repository.TransactionRepository
import br.com.finflow.transaction.service.CategorizationService
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
@RequestMapping("/api/transactions")
@Tag(
    name = "Transações",
    description = """
        Consulta, categorização e correção de transações extraídas dos documentos.

        **Categorias do sistema** (predefinidas, não editáveis):
        Salário, 13º Salário, Férias, PLR/Bônus, Restituição IR, Vale Refeição, Vale Alimentação,
        Moradia, Alimentação, Transporte, Saúde, Lazer, Educação, Outros

        **Aprendizado automático**: ao corrigir uma categoria via `PATCH /{id}/category`,
        o sistema cria uma regra de palavra-chave para categorizar automaticamente transações futuras similares.
    """
)
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val categorizationService: CategorizationService
) {

    @GetMapping
    @Operation(
        summary = "Listar transações por período",
        description = """
            Retorna todas as transações do usuário no período informado, ordenadas por data decrescente.

            Cada transação indica:
            - `categoryConfirmed = null` → categorizada por IA, aguardando confirmação do usuário
            - `categoryConfirmed = true` → usuário confirmou ou definiu a categoria
            - `categoryConfirmed = false` → usuário corrigiu a categoria
            - `categoryId = null` → ainda sem categoria (processe via `POST /categorize`)
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Lista de transações do período",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    name = "Dezembro/2025 — extrato Itaú",
                    summary = "Transações reais extraídas do extrato itau_extrato_072025.pdf",
                    value = """[
  {
    "id": "e1f2a3b4-c5d6-7890-efab-c12345678904",
    "date": "2025-12-19",
    "description": "FOLHA PAGAMENTO MENSAL",
    "amount": 11474.71,
    "type": "INCOME",
    "categoryId": "uuid-categoria-salario",
    "categoryName": "Salário",
    "categoryConfirmed": null
  },
  {
    "id": "f2a3b4c5-d6e7-8901-fabc-d23456789005",
    "date": "2025-12-19",
    "description": "FOLHA PAGTO 13. SALARIO",
    "amount": 4318.46,
    "type": "INCOME",
    "categoryId": "uuid-categoria-13-salario",
    "categoryName": "13º Salário",
    "categoryConfirmed": null
  },
  {
    "id": "a3b4c5d6-e7f8-9012-abcd-e34567890006",
    "date": "2025-12-29",
    "description": "ITAU BLACK 3111-6617",
    "amount": 14747.25,
    "type": "EXPENSE",
    "categoryId": "uuid-categoria-outros",
    "categoryName": "Outros",
    "categoryConfirmed": null
  },
  {
    "id": "b4c5d6e7-f8a9-0123-bcde-f45678901007",
    "date": "2025-12-12",
    "description": "DA ELETROPAULO 44380964",
    "amount": 236.42,
    "type": "EXPENSE",
    "categoryId": "uuid-categoria-moradia",
    "categoryName": "Moradia",
    "categoryConfirmed": null
  },
  {
    "id": "c5d6e7f8-a9b0-1234-cdef-056789012008",
    "date": "2025-12-10",
    "description": "PAG BOLETO ACREDITANDO CENTRO DE RECUPE",
    "amount": 1776.00,
    "type": "EXPENSE",
    "categoryId": "uuid-categoria-saude",
    "categoryName": "Saúde",
    "categoryConfirmed": null
  }
]"""
                )]
            )]
        ),
        ApiResponse(responseCode = "400", description = "Parâmetros de data inválidos ou ausentes"),
        ApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado")
    )
    fun list(
        @Parameter(description = "Data início do período (ISO 8601)", example = "2025-12-01", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) start: LocalDate,
        @Parameter(description = "Data fim do período (ISO 8601)", example = "2025-12-31", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) end: LocalDate,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<List<TransactionDto>> {
        val userId = UUID.fromString(principal.username)
        val transactions = transactionRepository.findByUserAndPeriod(userId, start, end)
        return ResponseEntity.ok(transactions.map { TransactionDto.from(it) })
    }

    @PatchMapping("/{id}/category")
    @Operation(
        summary = "Corrigir categoria de uma transação",
        description = """
            Atualiza a categoria de uma transação e marca `categoryConfirmed = true`.

            **Aprendizado automático**: se a nova categoria for diferente da atual,
            o sistema extrai palavras-chave da descrição e cria uma regra para transações futuras.

            **Exemplo**: ao categorizar "DA ELETROPAULO 44380964" como "Moradia",
            a palavra "eletropaulo" passa a categorizar automaticamente transações futuras com esse termo.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Categoria atualizada com sucesso"),
        ApiResponse(
            responseCode = "400",
            description = "Body inválido",
            content = [Content(
                examples = [ExampleObject(value = """{"error": "categoryId é obrigatório"}""")]
            )]
        ),
        ApiResponse(responseCode = "403", description = "Transação não pertence ao usuário autenticado"),
        ApiResponse(responseCode = "404", description = "Transação ou categoria não encontrada")
    )
    fun updateCategory(
        @Parameter(description = "UUID da transação", example = "a3b4c5d6-e7f8-9012-abcd-e34567890006")
        @PathVariable id: UUID,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "UUID da nova categoria",
            content = [Content(
                examples = [ExampleObject(
                    name = "Reclassificar 'ITAU BLACK' como Moradia",
                    value = """{"categoryId": "uuid-categoria-moradia"}"""
                )]
            )]
        )
        @RequestBody request: UpdateCategoryRequest,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(principal.username)
        categorizationService.confirmCategory(id, request.categoryId, userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/categorize")
    @Operation(
        summary = "Disparar categorização das transações pendentes",
        description = """
            Categoriza todas as transações do usuário que ainda não têm categoria.

            **Ordem de prioridade:**
            1. Regras do usuário por palavra-chave (aprendizado prévio)
            2. Regras do sistema (ex: "FOLHA PAGAMENTO" → Salário, "ELETROPAULO" → Moradia)
            3. Claude Haiku via AWS Bedrock (apenas para as que não foram resolvidas pelas regras)

            O processo é síncrono e pode demorar alguns segundos dependendo do volume.
            O custo de IA é minimizado pelas camadas 1 e 2 — a maioria das transações
            do extrato Itaú é resolvida sem chamar a AWS.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Categorização disparada",
            content = [Content(
                examples = [ExampleObject(value = """{"status": "categorization triggered"}""")]
            )]
        ),
        ApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado")
    )
    fun triggerCategorization(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Map<String, String>> {
        val userId = UUID.fromString(principal.username)
        categorizationService.categorizeAll(userId)
        return ResponseEntity.ok(mapOf("status" to "categorization triggered"))
    }
}
