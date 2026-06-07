package br.com.finflow.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Captura qualquer exceção não tratada e retorna JSON estruturado.
 * Sem isso, Spring MVC encaminha para /error que o Spring Security redireciona para /login.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("BAD_REQUEST", ex.message ?: "Invalid request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("NOT_FOUND", ex.message ?: "Resource not found"))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", ex.message ?: "Access denied"))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        // Loga a causa raiz para debugging
        val rootCause = generateSequence<Throwable>(ex) { it.cause }.last()
        return ResponseEntity.internalServerError().body(
            ErrorResponse(
                code = ex.javaClass.simpleName,
                message = ex.message ?: "Unexpected error",
                detail = rootCause.message?.take(200)
            )
        )
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String? = null
)
