package br.com.finflow.auth.service

import br.com.finflow.auth.model.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * Wrapper que adiciona o User interno do FinFlow ao OAuth2User do Spring.
 * Necessário para acessar o User no handler de sucesso sem nova query ao banco.
 */
class FinFlowOAuth2User(
    private val delegate: OAuth2User,
    val user: User
) : OAuth2User by delegate
