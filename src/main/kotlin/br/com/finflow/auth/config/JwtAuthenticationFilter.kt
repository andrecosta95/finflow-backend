package br.com.finflow.auth.config

import br.com.finflow.auth.repository.UserRepository
import br.com.finflow.auth.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * JwtAuthenticationFilter — intercepta toda requisição e valida o JWT no header Authorization.
 *
 * Se o token é válido:
 * - Extrai o userId do subject
 * - Carrega o usuário do banco (ou poderia usar só o JWT — trade-off: freshness vs performance)
 * - Registra a autenticação no SecurityContextHolder
 *
 * Se não há token ou é inválido, a requisição continua sem autenticação
 * (Spring Security rejeita nas rotas protegidas).
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ")

        runCatching {
            if (jwtService.isTokenValid(token)) {
                val userId = jwtService.extractUserId(token)
                val user = userRepository.findById(userId).orElse(null) ?: return@runCatching

                val principal = User(user.id.toString(), "", emptyList())
                val authToken = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                    .also { it.details = WebAuthenticationDetailsSource().buildDetails(request) }

                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        filterChain.doFilter(request, response)
    }
}
