package br.com.finflow.investment.parser

import br.com.finflow.investment.model.InvestmentProduct
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ItauInvestmentParser — parser para extrato de investimentos Itaú Ion.
 *
 * Formato detectado: PDFs exportados pelo app Itaú Ion com layout de carteira.
 * Regex ajustados para o formato "DD/MM/YYYY" e valores "R$ X.XXX,XX".
 *
 * Para adicionar suporte a outro banco: crie uma nova classe implementando InvestmentParser.
 * O InvestmentService descobre automaticamente via injeção da lista List<InvestmentParser>.
 */
@Component
class ItauInvestmentParser : InvestmentParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Detecta linhas como: "CDB IPCA+ 6,5% a.a.  R$ 15.432,00  R$ 14.000,00  10,23%  01/03/2027"
    private val productPattern = Regex(
        """(CDB|LCI|LCA|PGBL|VGBL|Fundo|FI|Tesouro|ETF)[^\n]+R\$\s*([\d.]+,\d{2})"""
    )
    private val datePattern = Regex("""(\d{2}/\d{2}/\d{4})""")

    override fun supports(bank: String, fileName: String): Boolean {
        val name = fileName.lowercase()
        val isItau = bank.contains("itau", ignoreCase = true) || name.contains("itau")
        val isInvestmentFile = name.contains("investimento") || name.contains("carteira") ||
            name.contains("portfolio") || name.contains("ion")
        return isItau && isInvestmentFile
    }

    override fun parse(bytes: ByteArray): List<ParsedInvestment> {
        val text = Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().getText(doc)
        }

        return productPattern.findAll(text).mapNotNull { match ->
            runCatching {
                val line = match.value
                val productType = detectProductType(line)
                val balance = parseAmount(match.groupValues[2])
                val dates = datePattern.findAll(line).map {
                    LocalDate.parse(it.value, dateFormatter)
                }.toList()

                ParsedInvestment(
                    bank = "Itaú",
                    productName = line.substringBefore("R$").trim(),
                    productType = productType,
                    currentBalance = balance,
                    investedAmount = null,
                    profitabilityPct = extractProfitability(line),
                    referenceDate = LocalDate.now(),
                    maturityDate = dates.firstOrNull()
                )
            }.getOrNull()
        }.toList()
    }

    private fun detectProductType(line: String): InvestmentProduct.ProductType = when {
        line.contains("CDB", ignoreCase = true)    -> InvestmentProduct.ProductType.CDB
        line.contains("LCI", ignoreCase = true)    -> InvestmentProduct.ProductType.LCI
        line.contains("LCA", ignoreCase = true)    -> InvestmentProduct.ProductType.LCA
        line.contains("PGBL", ignoreCase = true)   -> InvestmentProduct.ProductType.PGBL
        line.contains("VGBL", ignoreCase = true)   -> InvestmentProduct.ProductType.VGBL
        line.contains("Tesouro", ignoreCase = true) -> InvestmentProduct.ProductType.TREASURY
        line.contains("ETF", ignoreCase = true)    -> InvestmentProduct.ProductType.ETF
        line.contains("Fundo", ignoreCase = true) ||
        line.contains("FI ", ignoreCase = true)    -> InvestmentProduct.ProductType.FI
        else -> InvestmentProduct.ProductType.OTHER
    }

    private fun parseAmount(raw: String): BigDecimal =
        raw.replace(".", "").replace(",", ".").toBigDecimal()

    private fun extractProfitability(line: String): BigDecimal? {
        val pctPattern = Regex("""(\d+,\d+)%""")
        return pctPattern.find(line)?.groupValues?.get(1)
            ?.replace(",", ".")?.toBigDecimalOrNull()
    }
}
