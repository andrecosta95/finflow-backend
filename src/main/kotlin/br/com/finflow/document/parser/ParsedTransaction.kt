package br.com.finflow.document.parser

import java.math.BigDecimal
import java.time.LocalDate

/**
 * ParsedTransaction — resultado bruto do parser antes de salvar no banco.
 * Ainda sem categoria — a categorização acontece em etapa separada (F2).
 */
data class ParsedTransaction(
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val type: Type
) {
    enum class Type { INCOME, EXPENSE }
}
