package br.com.finflow.transaction.service

import br.com.finflow.transaction.model.Category
import br.com.finflow.transaction.model.Transaction
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest

/**
 * BedrockCategorizationService — categoriza transações usando Claude Haiku via AWS Bedrock.
 *
 * Usado apenas como fallback quando as regras locais não identificam a categoria.
 * Para reduzir custo, agrupa até 20 transações por chamada (batch prompt).
 *
 * ⚠️ Custo estimado: ~R$ 0,01 por 100 transações com Claude Haiku.
 */
@Service
class BedrockCategorizationService(
    private val bedrockClient: BedrockRuntimeClient,
    @Value("\${app.aws.bedrock.model-id}") private val modelId: String
) {
    private val mapper = jacksonObjectMapper()

    fun categorizeBatch(transactions: List<Transaction>, categories: List<Category>) {
        if (transactions.isEmpty()) return

        val categoryNames = categories.map { it.name }.distinct()
        val descriptions = transactions.mapIndexed { i, t -> "$i: ${t.description}" }

        val prompt = buildPrompt(descriptions, categoryNames)
        val result = runCatching { callBedrock(prompt) }.getOrNull() ?: return

        // Aplica as categorias retornadas pelo modelo
        result.forEach { (index, categoryName) ->
            val transaction = transactions.getOrNull(index) ?: return@forEach
            val category = categories.firstOrNull {
                it.name.equals(categoryName, ignoreCase = true)
            } ?: categories.firstOrNull { it.name == "Outros" }

            transaction.category = category
            transaction.categoryConfirmed = null
        }
    }

    private fun buildPrompt(descriptions: List<String>, categories: List<String>): String = """
        Categorize as transações financeiras abaixo.

        Categorias disponíveis: ${categories.joinToString(", ")}

        Transações:
        ${descriptions.joinToString("\n")}

        Responda APENAS com JSON no formato:
        {"0": "Categoria", "1": "Categoria", ...}

        Use exatamente os nomes das categorias fornecidas. Se não souber, use "Outros".
    """.trimIndent()

    private fun callBedrock(prompt: String): Map<Int, String> {
        val body = mapper.writeValueAsString(mapOf(
            "anthropic_version" to "bedrock-2023-05-31",
            "max_tokens" to 500,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt))
        ))

        val response = bedrockClient.invokeModel(
            InvokeModelRequest.builder()
                .modelId(modelId)
                .body(SdkBytes.fromUtf8String(body))
                .build()
        )

        val responseBody = mapper.readValue<Map<String, Any>>(response.body().asUtf8String())
        val content = (responseBody["content"] as? List<*>)
            ?.firstOrNull()
            ?.let { (it as? Map<*, *>)?.get("text") as? String }
            ?: return emptyMap()

        // Extrai JSON da resposta (o modelo pode incluir texto extra)
        val jsonRegex = Regex("""\{[^}]+\}""")
        val json = jsonRegex.find(content)?.value ?: return emptyMap()

        return mapper.readValue<Map<String, String>>(json)
            .mapKeys { it.key.toIntOrNull() ?: return emptyMap() }
    }
}
