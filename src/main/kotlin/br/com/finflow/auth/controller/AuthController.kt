package br.com.finflow.auth.controller

import br.com.finflow.auth.dto.AuthResponse
import br.com.finflow.auth.dto.UserDto
import br.com.finflow.auth.repository.UserRepository
import br.com.finflow.auth.service.JwtService
import br.com.finflow.auth.service.RefreshTokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login social (Google/Facebook), renovação e revogação de tokens JWT")
class AuthController(
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val userRepository: UserRepository,
    @Value("\${app.jwt.refresh-token-expiration}") private val refreshExpiration: Long
) {

    @GetMapping("/me")
    @Operation(
        summary = "Perfil do usuário autenticado",
        description = """
            Retorna os dados do usuário dono do JWT informado no header `Authorization`.
            Use este endpoint logo após o login para obter o perfil completo e descobrir o plano ativo.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Usuário autenticado",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    name = "Usuário plano Free",
                    value = """{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "andre@email.com",
  "name": "André Luiz Costa",
  "avatarUrl": "https://lh3.googleusercontent.com/a/foto.jpg",
  "plan": "FREE"
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "401", description = "Token ausente, expirado ou inválido")
    )
    fun me(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<UserDto> {
        val userId = UUID.fromString(principal.username)
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }
        return ResponseEntity.ok(UserDto.from(user))
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Renovar access token",
        description = """
            Gera um novo `access_token` JWT a partir do `refresh_token` armazenado em cookie httpOnly.

            **Rotação de tokens**: o refresh token atual é revogado e um novo é emitido.
            Isso significa que cada chamada a este endpoint invalida o token anterior —
            proteção contra roubo de refresh token.

            O cookie `refresh_token` é enviado automaticamente pelo browser (SameSite=Strict).
            Para clientes mobile, o token pode ser enviado no cookie ou no body como fallback.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Novo access token gerado",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    name = "Token renovado com sucesso",
                    value = """{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhMWIyYzNkNC1lNWY2LTc4OTAiLCJlbWFpbCI6ImFuZHJlQGVtYWlsLmNvbSIsIm5hbWUiOiJBbmRyw6kgTHVpeiBDb3N0YSIsInBsYW4iOiJGUkVFIiwiaWF0IjoxNzQ5MjQwMDAwLCJleHAiOjE3NDkyNDkwMDB9.assinatura",
  "user": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "andre@email.com",
    "name": "André Luiz Costa",
    "avatarUrl": "https://lh3.googleusercontent.com/a/foto.jpg",
    "plan": "FREE"
  }
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "400", description = "Cookie `refresh_token` ausente"),
        ApiResponse(responseCode = "401", description = "Refresh token expirado ou revogado")
    )
    fun refresh(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<AuthResponse> {
        val rawToken = request.cookies
            ?.firstOrNull { it.name == "refresh_token" }?.value
            ?: throw IllegalArgumentException("Refresh token não encontrado")

        val (user, newRefreshToken) = refreshTokenService.rotate(rawToken, request.getHeader("User-Agent"))
        val accessToken = jwtService.generateAccessToken(user)

        val cookie = Cookie("refresh_token", newRefreshToken).apply {
            isHttpOnly = true; secure = true; path = "/api/auth"
            maxAge = refreshExpiration.toInt(); setAttribute("SameSite", "Strict")
        }
        response.addCookie(cookie)

        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, user = UserDto.from(user)))
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout — revogar sessão",
        description = """
            Revoga **todos** os refresh tokens do usuário (logout de todos os dispositivos)
            e apaga o cookie `refresh_token`.

            Após o logout, o access token atual ainda é válido até expirar (máx 15 min).
            Para invalidação imediata em endpoints críticos, consulte o Redis de tokens revogados.
        """,
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Logout realizado com sucesso"),
        ApiResponse(responseCode = "401", description = "Token inválido ou expirado")
    )
    fun logout(
        @AuthenticationPrincipal principal: UserDetails,
        response: HttpServletResponse
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(principal.username)
        val user = userRepository.findById(userId).orElseThrow()
        refreshTokenService.revokeAll(user)

        val cookie = Cookie("refresh_token", "").apply {
            isHttpOnly = true; secure = true; path = "/api/auth"; maxAge = 0
        }
        response.addCookie(cookie)
        return ResponseEntity.noContent().build()
    }
}
