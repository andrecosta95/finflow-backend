package br.com.finflow.investment.service

import br.com.finflow.auth.repository.UserRepository
import br.com.finflow.document.model.Document
import br.com.finflow.document.parser.RawInvestment
import br.com.finflow.investment.model.InvestmentProduct
import br.com.finflow.investment.parser.InvestmentParser
import br.com.finflow.investment.repository.InvestmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class InvestmentService(
    private val investmentRepository: InvestmentRepository,
    private val userRepository: UserRepository,
    private val parsers: List<InvestmentParser>   // Todos os parsers injetados pelo Spring
) {

    @Transactional
    fun parseAndSave(document: Document, bytes: ByteArray, bank: String): List<InvestmentProduct> {
        val parser = parsers.firstOrNull { it.supports(bank, document.fileName) }
            ?: throw IllegalStateException("Nenhum parser de investimento para banco: $bank")

        val parsed = parser.parse(bytes)

        val products = parsed.map { p ->
            InvestmentProduct(
                user = document.user,
                document = document,
                bank = p.bank,
                productName = p.productName,
                productType = p.productType,
                currentBalance = p.currentBalance,
                investedAmount = p.investedAmount,
                profitabilityPct = p.profitabilityPct,
                referenceDate = p.referenceDate,
                maturityDate = p.maturityDate
            )
        }

        return investmentRepository.saveAll(products)
    }

    /**
     * Salva produtos de investimento extraídos de uma imagem pelo ImageAnalysisService.
     * Usado quando o upload é IMAGE (captura de tela de app bancário).
     */
    @Transactional
    fun saveFromImageData(document: Document, rawInvestments: List<RawInvestment>): List<InvestmentProduct> {
        val products = rawInvestments.map { raw ->
            val productType = runCatching {
                InvestmentProduct.ProductType.valueOf(raw.productType)
            }.getOrDefault(InvestmentProduct.ProductType.OTHER)

            InvestmentProduct(
                user = document.user,
                document = document,
                bank = raw.bank ?: "Desconhecido",
                productName = raw.productName,
                productType = productType,
                currentBalance = BigDecimal.valueOf(raw.currentBalance),
                investedAmount = null,
                profitabilityPct = raw.profitabilityPct?.let { BigDecimal.valueOf(it) },
                referenceDate = LocalDate.now()
            )
        }
        return investmentRepository.saveAll(products)
    }

    fun getPortfolio(userId: UUID): PortfolioSummary {
        val products = investmentRepository.findByUserIdOrderByCurrentBalanceDesc(userId)
        val totalBalance = products.sumOf { it.currentBalance }
        val totalInvested = products.mapNotNull { it.investedAmount }.sumOf { it }

        val byBank = investmentRepository.sumByBank(userId)
            .map { row -> BankAllocation(row[0] as String, row[1] as BigDecimal) }

        val byType = investmentRepository.sumByProductType(userId)
            .map { row -> TypeAllocation(
                (row[0] as InvestmentProduct.ProductType).name,
                row[1] as BigDecimal
            )}

        return PortfolioSummary(
            totalBalance = totalBalance,
            totalInvested = totalInvested,
            globalProfitabilityPct = if (totalInvested > BigDecimal.ZERO)
                ((totalBalance - totalInvested) / totalInvested * BigDecimal("100"))
            else null,
            products = products,
            byBank = byBank,
            byType = byType
        )
    }
}

data class PortfolioSummary(
    val totalBalance: BigDecimal,
    val totalInvested: BigDecimal,
    val globalProfitabilityPct: BigDecimal?,
    val products: List<InvestmentProduct>,
    val byBank: List<BankAllocation>,
    val byType: List<TypeAllocation>
)

data class BankAllocation(val bank: String, val total: BigDecimal)
data class TypeAllocation(val type: String, val total: BigDecimal)
