package br.com.finflow.document.parser

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * XlsxParser — lê planilhas Excel (.xlsx, .csv) com Apache POI.
 *
 * Suporta dois layouts comuns de exportação bancária:
 * - Coluna única de valor (positivo = receita, negativo = despesa)
 * - Colunas separadas de débito/crédito
 *
 * A detecção de layout ocorre automaticamente pela presença de colunas.
 */
@Component
class XlsxParser : DocumentParser {

    override fun supports(fileName: String, mimeType: String): Boolean =
        mimeType in setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv"
        ) || fileName.endsWith(".xlsx", true) || fileName.endsWith(".csv", true)

    override fun parse(bytes: ByteArray): List<ParsedTransaction> {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // Primeira linha não vazia = cabeçalho
            val headerRow = sheet.firstOrNull { row ->
                row.any { it.cellType == CellType.STRING && it.stringCellValue.isNotBlank() }
            } ?: return emptyList()

            val headers = headerRow.map { it.stringCellValue.trim().lowercase() }
            val dateCol    = headers.indexOfFirst { "data" in it }
            val descCol    = headers.indexOfFirst { "descri" in it || "histor" in it || "lançament" in it }
            val valueCol   = headers.indexOfFirst { it == "valor" || it == "value" }
            val debitCol   = headers.indexOfFirst { "débito" in it || "debito" in it || "saída" in it }
            val creditCol  = headers.indexOfFirst { "crédito" in it || "credito" in it || "entrada" in it }

            if (dateCol < 0 || descCol < 0) return emptyList()

            val transactions = mutableListOf<ParsedTransaction>()

            for (row in sheet.drop(headerRow.rowNum + 1)) {
                runCatching {
                    val date = when {
                        row.getCell(dateCol)?.cellType == CellType.NUMERIC ->
                            row.getCell(dateCol).dateCellValue
                                .toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                        else -> LocalDate.parse(row.getCell(dateCol)?.stringCellValue?.trim() ?: return@runCatching)
                    }

                    val description = row.getCell(descCol)?.stringCellValue?.trim() ?: return@runCatching

                    val (amount, type) = when {
                        valueCol >= 0 -> {
                            val v = row.getCell(valueCol)?.numericCellValue?.toBigDecimal() ?: return@runCatching
                            v.abs() to if (v < BigDecimal.ZERO) ParsedTransaction.Type.EXPENSE else ParsedTransaction.Type.INCOME
                        }
                        debitCol >= 0 && creditCol >= 0 -> {
                            val debit  = row.getCell(debitCol)?.numericCellValue ?: 0.0
                            val credit = row.getCell(creditCol)?.numericCellValue ?: 0.0
                            if (debit > 0) debit.toBigDecimal() to ParsedTransaction.Type.EXPENSE
                            else credit.toBigDecimal() to ParsedTransaction.Type.INCOME
                        }
                        else -> return@runCatching
                    }

                    transactions.add(ParsedTransaction(date, description, amount, type))
                }
            }

            return transactions
        }
    }
}
