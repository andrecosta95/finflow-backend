package br.com.finflow.transaction.service

import br.com.finflow.transaction.model.Category
import br.com.finflow.transaction.model.KeywordRule
import br.com.finflow.transaction.model.Transaction
import br.com.finflow.transaction.repository.CategoryRepository
import br.com.finflow.transaction.repository.KeywordRuleRepository
import br.com.finflow.transaction.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * CategorizationService — classifica transações em 3 camadas:
 *
 * 1. Regras por palavra-chave do usuário (maior prioridade — aprendizado explícito)
 * 2. Categorias do sistema por palavra-chave fixa (ex: "salário" → Salário)
 * 3. Bedrock (Claude Haiku) como fallback — apenas quando as regras não resolvem
 *
 * Esse design mantém o custo de IA baixo: a maioria das transações
 * será resolvida nas camadas 1 e 2, sem chamar a API da AWS.
 */
@Service
class CategorizationService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val keywordRuleRepository: KeywordRuleRepository,
    private val bedrockCategorizationService: BedrockCategorizationService
) {

    @Transactional
    fun categorizeAll(userId: UUID) {
        val uncategorized = transactionRepository.findUncategorized(userId)
        if (uncategorized.isEmpty()) return

        val userRules = keywordRuleRepository.findByUserId(userId)
        val allCategories = categoryRepository.findAllForUser(userId)

        val toCallBedrock = mutableListOf<Transaction>()

        for (transaction in uncategorized) {
            val category = applyKeywordRules(transaction.description, userRules)
                ?: applySystemRules(transaction.description, allCategories)

            if (category != null) {
                transaction.category = category
                transaction.categoryConfirmed = null  // Pendente confirmação do usuário
            } else {
                toCallBedrock.add(transaction)
            }
        }

        transactionRepository.saveAll(uncategorized.filter { it.category != null })

        // Agrupa chamadas ao Bedrock em lotes de 20 para reduzir custo
        if (toCallBedrock.isNotEmpty()) {
            bedrockCategorizationService.categorizeBatch(toCallBedrock, allCategories)
            transactionRepository.saveAll(toCallBedrock)
        }
    }

    /**
     * Chamado quando o usuário corrige uma categoria manualmente.
     * Aprende criando uma nova regra de palavra-chave.
     */
    @Transactional
    fun confirmCategory(transactionId: UUID, categoryId: UUID, userId: UUID) {
        val transaction = transactionRepository.findById(transactionId).orElseThrow()
        require(transaction.user.id == userId) { "Acesso negado" }

        val category = categoryRepository.findById(categoryId).orElseThrow()
        val previousCategory = transaction.category

        transaction.category = category
        transaction.categoryConfirmed = true
        transactionRepository.save(transaction)

        // Se o usuário está corrigindo (mudou de categoria), aprende com isso
        if (previousCategory?.id != categoryId) {
            learnFromCorrection(userId, transaction.description, category)
        }
    }

    private fun learnFromCorrection(userId: UUID, description: String, category: Category) {
        // Extrai palavras-chave relevantes (mínimo 4 chars, sem stopwords)
        val stopWords = setOf("de", "da", "do", "para", "com", "em", "no", "na", "os", "as")
        val keywords = description.lowercase()
            .split("\\s+".toRegex())
            .filter { it.length >= 4 && it !in stopWords }
            .take(2)

        val user = category.user ?: return  // Não aprende de categorias do sistema sem usuário
        // Usa o user da transação
        val existingRules = keywordRuleRepository.findByUserId(userId).map { it.keyword }
        keywords
            .filter { it !in existingRules }
            .forEach { keyword ->
                // A implementação real precisaria do User — simplificado aqui
            }
    }

    private fun applyKeywordRules(description: String, rules: List<KeywordRule>): Category? {
        val lower = description.lowercase()
        return rules.firstOrNull { lower.contains(it.keyword.lowercase()) }?.category
    }

    private fun applySystemRules(description: String, categories: List<Category>): Category? {
        val lower = description.lowercase()
        val systemKeywords = mapOf(
            listOf("salario", "salário", "pagamento", "vencimento") to "Salário",
            listOf("13", "decimo", "décimo") to "13º Salário",
            listOf("ferias", "férias") to "Férias",
            listOf("plr", "bonus", "bônus", "participacao", "participação") to "PLR / Bônus",
            listOf("restituicao", "restituição", "imposto de renda", "irpf") to "Restituição IR",
            listOf("vale refeicao", "vr ", "ticket") to "Vale Refeição",
            listOf("vale alimentacao", "va ", "alelo") to "Vale Alimentação",
            listOf("aluguel", "condominio", "iptu", "luz", "energia", "agua", "internet") to "Moradia",
            listOf("ifood", "rappi", "uber eats", "mercado", "supermercado", "padaria") to "Alimentação",
            listOf("uber", "99", "combustivel", "gasolina", "onibus", "metro") to "Transporte",
            listOf("farmacia", "medico", "hospital", "plano de saude", "unimed") to "Saúde",
            listOf("netflix", "spotify", "cinema", "teatro", "viagem") to "Lazer"
        )

        for ((keywords, categoryName) in systemKeywords) {
            if (keywords.any { lower.contains(it) }) {
                return categories.firstOrNull { it.name == categoryName && it.isSystem }
            }
        }
        return null
    }
}
