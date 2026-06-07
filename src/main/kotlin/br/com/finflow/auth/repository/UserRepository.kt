package br.com.finflow.auth.repository

import br.com.finflow.auth.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): Optional<User>

    // Busca usuário pelo ID do provedor OAuth (ex: Google subject ID)
    @Query("""
        SELECT u FROM User u
        JOIN u.oauthProviders op
        WHERE op.provider = :provider AND op.providerId = :providerId
    """)
    fun findByOAuthProvider(provider: String, providerId: String): Optional<User>

    fun existsByEmail(email: String): Boolean
}
