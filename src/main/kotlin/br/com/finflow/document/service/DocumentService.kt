package br.com.finflow.document.service

import br.com.finflow.auth.model.User
import br.com.finflow.auth.repository.UserRepository
import br.com.finflow.document.model.Document
import br.com.finflow.document.parser.DocumentParser
import br.com.finflow.document.repository.DocumentRepository
import br.com.finflow.investment.parser.InvestmentParser
import br.com.finflow.investment.service.InvestmentService
import br.com.finflow.transaction.model.Transaction
import br.com.finflow.transaction.repository.TransactionRepository
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val transactionRepository: TransactionRepository,
    private val storageService: StorageService,
    private val parsers: List<DocumentParser>,
    private val investmentParsers: List<InvestmentParser>,
    @Lazy private val investmentService: InvestmentService,
    private val userRepository: UserRepository
) {

    @Transactional
    fun upload(userId: UUID, file: MultipartFile): Document {
        val user = userRepository.findById(userId).orElseThrow()

        validateFile(file)

        val s3Key = storageService.upload(
            userId = userId,
            originalFileName = file.originalFilename ?: "upload",
            bytes = file.bytes,
            contentType = file.contentType ?: "application/octet-stream"
        )

        val fileType = detectFileType(file)

        val document = documentRepository.save(
            Document(
                user = user,
                fileName = file.originalFilename ?: "upload",
                fileType = fileType,
                s3Key = s3Key,
                status = Document.Status.PENDING
            )
        )

        // Processa de forma síncrona por ora (Sprint 2).
        // No Sprint seguinte moveremos para processamento assíncrono via SQS.
        processDocument(document, file.bytes)

        return document
    }

    @Transactional
    fun processDocument(document: Document, bytes: ByteArray) {
        document.status = Document.Status.PROCESSING
        documentRepository.save(document)

        // Verifica se é documento de investimentos antes de tentar parsear transações
        val bankHint = document.fileName.lowercase().let { name ->
            when {
                name.contains("itau") || name.contains("itaú") -> "itau"
                name.contains("bradesco") -> "bradesco"
                name.contains("nubank") -> "nubank"
                else -> ""
            }
        }
        val investmentParser = investmentParsers.firstOrNull {
            it.supports(bankHint, document.fileName)
        }
        if (investmentParser != null) {
            runCatching {
                investmentService.parseAndSave(document, bytes, bankHint)
                document.status = Document.Status.DONE
                document.parsedAt = Instant.now()
            }.onFailure { ex ->
                document.status = Document.Status.ERROR
                document.errorMessage = ex.message?.take(500)
            }
            documentRepository.save(document)
            return
        }

        runCatching {
            val parser = parsers.firstOrNull {
                it.supports(document.fileName, document.fileType.name)
            } ?: throw IllegalStateException("Nenhum parser disponível para ${document.fileName}")

            val parsed = parser.parse(bytes)

            if (parsed.isEmpty()) {
                throw IllegalStateException("Nenhuma transação encontrada no documento")
            }

            // Detecta duplicatas: mesma data + descrição + valor para o mesmo usuário
            val existing = transactionRepository
                .findByUserIdForDeduplication(document.user.id)
                .map { "${it.transactionDate}|${it.description}|${it.amount}" }
                .toSet()

            val transactions = parsed
                .filter { "${it.date}|${it.description}|${it.amount}" !in existing }
                .map { pt ->
                    Transaction(
                        user = document.user,
                        document = document,
                        transactionDate = pt.date,
                        description = pt.description,
                        amount = pt.amount,
                        type = if (pt.type == br.com.finflow.document.parser.ParsedTransaction.Type.INCOME)
                                   Transaction.Type.INCOME else Transaction.Type.EXPENSE
                    )
                }

            transactionRepository.saveAll(transactions)

            document.status = Document.Status.DONE
            document.parsedAt = Instant.now()
        }.onFailure { ex ->
            document.status = Document.Status.ERROR
            document.errorMessage = ex.message?.take(500)
        }

        documentRepository.save(document)
    }

    private fun validateFile(file: MultipartFile) {
        require(!file.isEmpty) { "Arquivo vazio" }
        require((file.size / 1024 / 1024) <= 10) { "Arquivo excede o limite de 10MB" }
        val allowed = setOf("application/pdf", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv", "image/jpeg", "image/png")
        require(file.contentType in allowed) { "Tipo de arquivo não suportado: ${file.contentType}" }
    }

    private fun detectFileType(file: MultipartFile): Document.FileType = when {
        file.contentType == "application/pdf" -> Document.FileType.PDF
        file.originalFilename?.endsWith(".xlsx", true) == true -> Document.FileType.XLSX
        file.originalFilename?.endsWith(".csv", true) == true -> Document.FileType.CSV
        file.contentType?.startsWith("image/") == true -> Document.FileType.IMAGE
        else -> Document.FileType.PDF
    }
}
