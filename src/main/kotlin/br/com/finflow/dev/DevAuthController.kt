package br.com.finflow.dev

import br.com.finflow.auth.model.User
import br.com.finflow.auth.repository.UserRepository
import br.com.finflow.auth.service.JwtService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * DevAuthController — disponível SOMENTE no perfil "local".
 *
 * Cria um usuário de teste e retorna um JWT válido sem passar pelo OAuth2.
 * Use o token retornado no botão "Authorize" do Swagger UI.
 */
@RestController
@RequestMapping("/dev")
@Profile("local")
@Tag(name = "DEV — apenas local", description = "Endpoints exclusivos para desenvolvimento. Não existe em produção.")
class DevAuthController(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    @PostMapping("/token")
    @Operation(
        summary = "Gerar JWT de teste (só funciona em local)",
        description = """
            Cria ou reutiliza o usuário de teste `dev@finflow.com.br` e retorna um JWT válido por 15 minutos.

            **Como usar no Swagger UI:**
            1. Chame este endpoint → copie o `accessToken`
            2. Clique em **Authorize** (cadeado 🔒) no topo da página
            3. Cole o token no campo `bearerAuth` → clique **Authorize**
            4. Todos os outros endpoints agora funcionam autenticados
        """
    )
    fun devToken(): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail("dev@finflow.com.br").orElseGet {
            userRepository.save(
                User(
                    email = "dev@finflow.com.br",
                    name = "André Luiz Costa",
                    avatarUrl = "https://ui-avatars.com/api/?name=André+Costa&background=0062cc&color=fff",
                    plan = User.Plan.FREE
                )
            )
        }
        val token = jwtService.generateAccessToken(user)
        return ResponseEntity.ok(mapOf(
            "accessToken" to token,
            "userId" to user.id.toString(),
            "instructions" to "Cole o accessToken no botão Authorize do Swagger UI"
        ))
    }
}
