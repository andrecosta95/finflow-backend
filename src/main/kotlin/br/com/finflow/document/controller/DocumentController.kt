package br.com.finflow.document.controller

import br.com.finflow.document.model.Document
import br.com.finflow.document.service.DocumentService
import br.com.finflow.document.service.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/documents")
@Tag(
    name = "Documentos",
    description = """
        Upload e parsing automático de extratos bancários e faturas de cartão.

        **Formatos suportados:**
        - PDF: extrato conta corrente Itaú, fatura Itaú Black/Mastercard, Nubank, Bradesco
        - XLSX/CSV: planilha Itaú Ion (exportada pelo app), XP, BTG, Rico
        - Imagens JPG/PNG: comprovantes e extratos escaneados (via OCR Textract)
        - Limite por arquivo: **10MB**

        Após o upload, o parsing ocorre de forma síncrona.
        As transações extraídas ficam disponíveis imediatamente em `GET /api/transactions`.
    """
)
class DocumentController(
    private val documentService: DocumentService,
    private val storageService: StorageService
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload de extrato ou fatura",
        description = """
            Recebe um arquivo (PDF, XLSX, CSV ou imagem), armazena no S3/MinIO
            e extrai automaticamente as transações usando PDFBox (PDF) ou Apache POI (XLSX).

            **Extrato conta corrente Itaú (PDF)**
            Formato esperado na linha:
            ```
            29/12/2025  FOLHA PAGAMENTO MENSAL  11.474,71
            29/12/2025  ITAU BLACK 3111-6617    -14.747,25
            ```

            **Fatura cartão Itaú Black**
            Formato das linhas de lançamento:
            ```
            08/03  MP *VERDURASBUCHO  14,00
            09/03  CENTAURO           98,56
            ```

            **Planilha de investimentos Itaú Ion (XLSX)**
            Colunas: `Data referência | Produto | Rendimento bruto (R$) | Rentabilidade bruta (%) | Saldo bruto (R$)`

            Transações duplicadas (mesma data + descrição + valor) são detectadas e ignoradas.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Arquivo processado com sucesso — transações extraídas",
            content = [Content(
                mediaType = "application/json",
                examples = [
                    ExampleObject(
                        name = "Extrato conta corrente processado",
                        summary = "PDF de extrato Itaú jul–dez 2025",
                        value = """{
  "id": "b3c4d5e6-f7a8-9012-bcde-f12345678901",
  "fileName": "itau_extrato_072025.pdf",
  "fileType": "PDF",
  "status": "DONE",
  "errorMessage": null,
  "parsedAt": "2026-06-07T18:05:00Z"
}"""
                    ),
                    ExampleObject(
                        name = "Fatura cartão processada",
                        summary = "Fatura Itaú Black mar/2026 — R$ 12.235,44",
                        value = """{
  "id": "c4d5e6f7-a8b9-0123-cdef-012345678902",
  "fileName": "Fatura_Itau_20260606.pdf",
  "fileType": "PDF",
  "status": "DONE",
  "errorMessage": null,
  "parsedAt": "2026-06-07T18:05:10Z"
}"""
                    ),
                    ExampleObject(
                        name = "Erro de parsing",
                        summary = "PDF protegido por senha ou corrompido",
                        value = """{
  "id": "d5e6f7a8-b9c0-1234-defa-123456789003",
  "fileName": "extrato_protegido.pdf",
  "fileType": "PDF",
  "status": "ERROR",
  "errorMessage": "Nenhuma transação encontrada no documento",
  "parsedAt": null
}"""
                    )
                ]
            )]
        ),
        ApiResponse(responseCode = "400", description = "Arquivo vazio, tipo não suportado ou excede 10MB"),
        ApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado")
    )
    fun upload(
        @Parameter(
            description = "Arquivo a ser processado (PDF, XLSX, CSV, JPG, PNG — máx 10MB)",
            required = true,
            schema = Schema(type = "string", format = "binary")
        )
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<DocumentResponse> {
        val userId = UUID.fromString(principal.username)
        val document = documentService.upload(userId, file)
        return ResponseEntity.ok(DocumentResponse.from(document))
    }

    @GetMapping("/{id}/download-url")
    @Operation(
        summary = "URL de download do documento original",
        description = """
            Gera uma URL pré-assinada para download direto do arquivo original armazenado no S3/MinIO.
            A URL expira em **15 minutos** e não requer autenticação adicional (útil para visualização no browser).

            Use esta URL para mostrar o PDF original ao usuário ao lado das transações extraídas.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "URL gerada com sucesso",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
  "url": "http://localhost:9000/finflow-documents/users/a1b2c3d4/documents/extrato.pdf?X-Amz-Expires=900&X-Amz-Signature=abc123"
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "404", description = "Documento não encontrado ou não pertence ao usuário")
    )
    fun downloadUrl(
        @Parameter(description = "UUID do documento", example = "b3c4d5e6-f7a8-9012-bcde-f12345678901")
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<Map<String, String>> {
        val url = storageService.generatePresignedUrl("users/${principal.username}/documents/$id")
        return ResponseEntity.ok(mapOf("url" to url))
    }
}

data class DocumentResponse(
    @Schema(description = "UUID do documento", example = "b3c4d5e6-f7a8-9012-bcde-f12345678901")
    val id: UUID,
    @Schema(description = "Nome original do arquivo", example = "itau_extrato_072025.pdf")
    val fileName: String,
    @Schema(description = "Tipo detectado", example = "PDF", allowableValues = ["PDF", "XLSX", "CSV", "IMAGE"])
    val fileType: String,
    @Schema(description = "Status do processamento", example = "DONE", allowableValues = ["PENDING", "PROCESSING", "DONE", "ERROR"])
    val status: String,
    @Schema(description = "Mensagem de erro (somente quando status=ERROR)", example = "null", nullable = true)
    val errorMessage: String?,
    @Schema(description = "Data/hora do parsing (ISO 8601)", example = "2026-06-07T18:05:00Z", nullable = true)
    val parsedAt: String?
) {
    companion object {
        fun from(doc: Document) = DocumentResponse(
            id = doc.id, fileName = doc.fileName, fileType = doc.fileType.name,
            status = doc.status.name, errorMessage = doc.errorMessage, parsedAt = doc.parsedAt?.toString()
        )
    }
}
