package br.com.finflow.investment.parser

import br.com.finflow.investment.model.InvestmentProduct
import java.math.BigDecimal
import java.time.LocalDate

/**
 * InvestmentParser — interface Strategy para parsers de extrato de investimento.
 *
 * Cada banco tem seu próprio layout de PDF/Excel, então cada parser
 * é uma implementação separada. Adicionar um novo banco = criar uma nova classe,
 * sem modificar código existente (Open/Closed Principle).
 */
interface InvestmentParser {
    fun parse(bytes: ByteArray): List<ParsedInvestment>
    fun supports(bank: String, fileName: String): Boolean
}

data class ParsedInvestment(
    val bank: String,
    val productName: String,
    val productType: InvestmentProduct.ProductType,
    val currentBalance: BigDecimal,
    val investedAmount: BigDecimal?,
    val profitabilityPct: BigDecimal?,
    val referenceDate: LocalDate,
    val maturityDate: LocalDate?
)
