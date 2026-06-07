package br.com.finflow.auth.config

import br.com.finflow.auth.service.FinFlowOAuth2User
import br.com.finflow.auth.service.JwtService
import br.com.finflow.auth.service.RefreshTokenService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * OAuth2SuccessHandler — o que acontece DEPOIS que o Google/Facebook autentica o usuário.
 *
 * Estratégia de segurança para tokens:
 * - Access token (JWT, 15 min): enviado como query param no redirect → frontend armazena em memória
 * - Refresh token (30 dias): enviado em cookie httpOnly, SameSite=Strict → invisível ao JavaScript
 *
 * Por que não cookie para o access token?
 * SPA precisa ler o JWT para extrair claims (nome, email) sem fazer request extra.
 * Cookie httpOnly impede leitura via JS — por isso o access vai na URL e o refresh no cookie.
 */
@Component
class OAuth2SuccessHandler(
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    @Value("\${app.jwt.refresh-token-expiration}") private val refreshExpiration: Long,
    @Value("\${app.frontend.url}") private val frontendUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val finFlowUser = authentication.principal as FinFlowOAuth2User
        val user = finFlowUser.user

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = refreshTokenService.create(user, request.getHeader("User-Agent"))

        // Refresh token em cookie httpOnly — JS nunca acessa
        val cookie = Cookie("refresh_token", refreshToken).apply {
            isHttpOnly = true
            secure = true          // HTTPS only (em prod)
            path = "/api/auth"     // Enviado apenas nas rotas de auth
            maxAge = refreshExpiration.toInt()
            setAttribute("SameSite", "Strict")
        }
        response.addCookie(cookie)

        // Redireciona para o frontend com o access token na URL
        val redirectUrl = UriComponentsBuilder
            .fromUriString("$frontendUrl/auth/callback")
            .queryParam("token", accessToken)
            .build().toUriString()

        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }
}
