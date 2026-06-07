package br.com.finflow.document.service

import br.com.finflow.document.parser.ImageAnalysisResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import java.util.Base64

/**
 * ImageAnalysisService — extrai dados financeiros de capturas de tela via AWS Bedrock.
 *
 * Usa Claude 3 Haiku (multimodal) para identificar:
 * - Investimentos: produto, saldo, rentabilidade, banco
 * - Transações: data, descrição, valor, tipo (entrada/saída)
 *
 * Requer credenciais AWS configuradas (IAM, ~/.aws/credentials ou variáveis de ambiente).
 * Sem credenciais, o upload de imagem falha com status ERROR e mensagem clara.
 */
@Service
class ImageAnalysisService(
    private val bedrockRuntimeClient: BedrockRuntimeClient,
    @Value("\${app.aws.bedrock.model-id}") private val modelId: String,
    @Value("\${app.aws.bedrock.enabled:true}") private val bedrockEnabled: Boolean,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(ImageAnalysisService::class.java)

    fun analyze(bytes: ByteArray, fileName: String): ImageAnalysisResult {
        if (!bedrockEnabled) {
            throw IllegalStateException(
                "Análise de imagem desabilitada. Configure credenciais AWS e defina " +
                "BEDROCK_ENABLED=true (ou app.aws.bedrock.enabled=true)"
            )
        }

        val mediaType = when {
            fileName.lowercase().endsWith(".png") -> "image/png"
            fileName.lowercase().endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }

        log.info("Analisando imagem '{}' via Bedrock ({})", fileName, modelId)

        val requestBody = buildRequestBody(Base64.getEncoder().encodeToString(bytes), mediaType)

        val response = try {
            bedrockRuntimeClient.invokeModel {
                it.modelId(modelId)
                it.contentType("application/json")
                it.accept("application/json")
                it.body(SdkBytes.fromUtf8String(requestBody))
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Erro ao chamar AWS Bedrock. Verifique credenciais AWS e região. " +
                "Detalhes: ${e.message?.take(200)}", e
            )
        }

        return parseResponse(response.body().asUtf8String(), fileName)
    }

    private fun buildRequestBody(base64Image: String, mediaType: String): String {
        val prompt = """
Você é um especialista em extrair dados financeiros de capturas de tela de aplicativos bancários brasileiros (Itaú, Nubank, XP, BTG, Bradesco, Inter, C6, Sicredi, etc.).

Analise esta imagem e extraia TODOS os dados financeiros visíveis.

Retorne APENAS um JSON válido, sem texto adicional antes ou depois, neste formato exato:

Para extratos de conta corrente (lançamentos):
{
  "type": "TRANSACTIONS",
  "bank": "nome do banco detectado ou null",
  "transactions": [
    {
      "date": "DD/MM/YYYY",
      "description": "favorecido ou descrição exata como aparece na tela",
      "amount": 2400.00,
      "type": "EXPENSE",
      "category": "Transferência Pessoal"
    }
  ],
  "investments": []
}

Para carteiras de investimento:
{
  "type": "INVESTMENTS",
  "bank": "Itaú",
  "transactions": [],
  "investments": [
    {"productName": "CDB BANCO BMG PRE 13.88% 27/02/2029", "productType": "CDB", "currentBalance": 20692.54, "profitabilityPct": 3.35, "bank": "Itaú"}
  ]
}

Tipos de produto válidos: CDB, LCI, LCA, PGBL, VGBL, FI, STOCKS, ETF, TREASURY, OTHER

Categorias de transação — use a mais específica possível:
- "Transferência Pessoal" → Pix ou TED para pessoa física
- "Pagamento de Boleto" → boleto bancário genérico
- "Impostos" → IOF, DARF, IPTU, IPVA, IR, etc.
- "Saúde" → clínicas, hospitais, farmácias, planos de saúde
- "Alimentação" → supermercados, restaurantes, delivery
- "Transporte" → combustível, estacionamento, Uber, táxi
- "Educação" → mensalidade escolar, cursos, material didático
- "Moradia" → aluguel, condomínio, água, luz, gás
- "Serviços" → assinaturas, telecomunicações, serviços gerais
- "Recebimento de Cliente" → Pix/TED recebido de pessoa jurídica ou pagamento por serviço
- "Recebimento Pessoal" → Pix/TED recebido de pessoa física
- "Salário" → folha de pagamento
- "Transferência entre Contas" → mesma titularidade
- "Outro" → não se encaixa nas anteriores

Regras:
- Débitos / gastos / saídas (valores com "-") = EXPENSE
- Créditos / entradas / rendimentos (valores sem "-" ou em verde) = INCOME
- Valores monetários: use ponto decimal (2400.00, NÃO 2.400,00)
- Datas: formato DD/MM/YYYY; se o extrato mostra "08 de junho de 2026" → "08/06/2026"
- description: use o favorecido/nome exato da tela. Para impostos, use o nome do imposto (ex: "IOF")
- Se não há dados de um tipo: array vazio []
- MIXED se a tela mostrar tanto transações quanto investimentos
- Detecte o banco pela interface (logo, nome, paleta de cores)
""".trimIndent()

        val root = objectMapper.createObjectNode().apply {
            put("anthropic_version", "bedrock-2023-05-31")
            put("max_tokens", 2000)
            putArray("messages").addObject().apply {
                put("role", "user")
                putArray("content").apply {
                    addObject().apply {
                        put("type", "image")
                        putObject("source").apply {
                            put("type", "base64")
                            put("media_type", mediaType)
                            put("data", base64Image)
                        }
                    }
                    addObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    }
                }
            }
        }
        return objectMapper.writeValueAsString(root)
    }

    private fun parseResponse(responseText: String, fileName: String): ImageAnalysisResult {
        return try {
            val root = objectMapper.readTree(responseText)
            val text = root["content"][0]["text"].asText()

            log.debug("Bedrock response for '{}': {}", fileName, text.take(500))

            // Claude pode incluir texto antes/depois do JSON — extraímos só o JSON
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            require(jsonStart >= 0 && jsonEnd > jsonStart) {
                "Bedrock não retornou JSON válido: ${text.take(300)}"
            }

            objectMapper.readValue(text.substring(jsonStart, jsonEnd), ImageAnalysisResult::class.java)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Erro ao interpretar resposta do Bedrock: ${e.message?.take(200)}", e)
        }
    }
}
