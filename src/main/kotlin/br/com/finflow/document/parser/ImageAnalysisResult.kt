package br.com.finflow.document.parser

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Resultado estruturado da análise de imagem via AWS Bedrock (Claude 3 Haiku vision).
 * Claude retorna JSON que é desserializado nesta classe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageAnalysisResult(

    /** TRANSACTIONS | INVESTMENTS | MIXED */
    val type: String = "MIXED",

    /** Banco detectado na interface (ex: "Itaú", "Nubank") */
    val bank: String? = null,

    val transactions: List<RawTransaction> = emptyList(),
    val investments: List<RawInvestment> = emptyList()
) {
    fun hasTransactions() = transactions.isNotEmpty()
    fun hasInvestments() = investments.isNotEmpty()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawTransaction(
    val date: String = "",
    val description: String = "",
    val amount: Double = 0.0,

    /** INCOME | EXPENSE */
    val type: String = "EXPENSE",

    /**
     * Categoria sugerida pela IA com base no tipo/favorecido, ex:
     * "Transferência Pessoal", "Pagamento de Boleto", "Impostos",
     * "Recebimento de Cliente", "Alimentação", "Saúde", etc.
     */
    val category: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawInvestment(
    val productName: String = "",

    /** CDB | LCI | LCA | PGBL | VGBL | FI | STOCKS | ETF | TREASURY | OTHER */
    val productType: String = "OTHER",

    val currentBalance: Double = 0.0,
    val profitabilityPct: Double? = null,
    val bank: String? = null
)
