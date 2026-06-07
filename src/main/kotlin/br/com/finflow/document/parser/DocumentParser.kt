package br.com.finflow.document.parser

/**
 * DocumentParser — interface comum para todos os parsers de documento.
 *
 * Por que interface?
 * Permite adicionar novos bancos/formatos sem modificar o código existente
 * (princípio Open/Closed). Cada parser é uma implementação independente.
 */
interface DocumentParser {
    fun parse(bytes: ByteArray): List<ParsedTransaction>
    fun supports(fileName: String, mimeType: String): Boolean
}
