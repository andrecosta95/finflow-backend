package br.com.finflow.document.parser

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * PdfParser — extrai transações de PDFs bancários usando PDFBox.
 *
 * Estratégia:
 * 1. Extrai texto bruto do PDF com PDFTextStripper
 * 2. Aplica regex para identificar linhas de transação
 * 3. Normaliza data, descrição e valor
 *
 * Limitação: PDFs com tabelas complexas ou colunas sobrepostas podem
 * precisar de Textract (AWS OCR). Isso é tratado no DocumentService
 * como fallback quando a extração local retorna < 3 transações.
 */
@Component
class PdfParser : DocumentParser {

    // Regex: captura linhas no formato "DD/MM/YYYY Descrição R$ 1.234,56"
    // ou "DD/MM/YYYY Descrição 1.234,56" (sem símbolo de moeda)
    private val transactionPattern = Regex(
        """(\d{2}/\d{2}/\d{4})\s+(.+?)\s+(-?\d{1,3}(?:\.\d{3})*,\d{2})"""
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun supports(fileName: String, mimeType: String): Boolean =
        mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)

    override fun parse(bytes: ByteArray): List<ParsedTransaction> {
        val text = extractText(bytes)
        return parseLines(text)
    }

    private fun extractText(bytes: ByteArray): String {
        Loader.loadPDF(bytes).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }

    internal fun parseLines(text: String): List<ParsedTransaction> {
        return transactionPattern.findAll(text).mapNotNull { match ->
            runCatching {
                val (dateStr, description, amountStr) = match.destructured
                val amount = amountStr
                    .replace(".", "")
                    .replace(",", ".")
                    .toBigDecimal()

                ParsedTransaction(
                    date = LocalDate.parse(dateStr, dateFormatter),
                    description = description.trim(),
                    amount = amount.abs(),
                    type = if (amount < BigDecimal.ZERO) ParsedTransaction.Type.EXPENSE
                           else ParsedTransaction.Type.INCOME
                )
            }.getOrNull()
        }.toList()
    }
}
