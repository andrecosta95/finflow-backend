package br.com.finflow.auth.service

import br.com.finflow.auth.model.OAuthProvider
import br.com.finflow.auth.model.User
import br.com.finflow.auth.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * OAuth2UserService — processa o usuário retornado pelo provedor após login social.
 *
 * Fluxo:
 * 1. Usuário clica "Entrar com Google"
 * 2. Google autentica e redireciona com code
 * 3. Spring troca code por access_token e chama /userinfo
 * 4. Este serviço recebe os dados do usuário e faz upsert no banco
 */
@Service
class OAuth2UserService(
    private val userRepository: UserRepository
) : DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val provider = userRequest.clientRegistration.registrationId  // "google", "facebook"

        val (providerId, email, name, avatarUrl) = extractAttributes(provider, oAuth2User)

        // Busca por provider ID → fallback para email → cria novo usuário
        val user = userRepository.findByOAuthProvider(provider, providerId)
            .orElseGet {
                userRepository.findByEmail(email).orElseGet {
                    createNewUser(email, name, avatarUrl, provider, providerId)
                }.also { existing ->
                    // Vincula novo provedor OAuth ao usuário existente
                    if (existing.oauthProviders.none { it.provider == provider }) {
                        existing.oauthProviders.add(OAuthProvider(user = existing, provider = provider, providerId = providerId))
                        userRepository.save(existing)
                    }
                }
            }

        // Devolve o OAuth2User com o ID interno do FinFlow como atributo extra
        return FinFlowOAuth2User(oAuth2User, user)
    }

    private fun createNewUser(email: String, name: String, avatarUrl: String?, provider: String, providerId: String): User {
        val user = User(email = email, name = name, avatarUrl = avatarUrl)
        user.oauthProviders.add(OAuthProvider(user = user, provider = provider, providerId = providerId))
        return userRepository.save(user)
    }

    private data class OAuthAttributes(
        val providerId: String,
        val email: String,
        val name: String,
        val avatarUrl: String?
    )

    private fun extractAttributes(provider: String, oAuth2User: OAuth2User): OAuthAttributes {
        val attrs = oAuth2User.attributes
        return when (provider) {
            "google" -> OAuthAttributes(
                providerId = attrs["sub"] as String,
                email = attrs["email"] as String,
                name = attrs["name"] as String,
                avatarUrl = attrs["picture"] as? String
            )
            "facebook" -> OAuthAttributes(
                providerId = attrs["id"] as String,
                email = attrs["email"] as String,
                name = attrs["name"] as String,
                avatarUrl = (attrs["picture"] as? Map<*, *>)
                    ?.get("data")?.let { (it as? Map<*, *>)?.get("url") as? String }
            )
            else -> throw IllegalArgumentException("Provedor OAuth não suportado: $provider")
        }
    }
}
