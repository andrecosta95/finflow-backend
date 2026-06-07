package br.com.finflow.auth.repository

import br.com.finflow.auth.model.RefreshToken
import br.com.finflow.auth.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    // Revoga todos os tokens de um usuário (usado no logout completo)
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.user = :user AND rt.revokedAt IS NULL")
    fun revokeAllByUser(user: User, now: Instant = Instant.now()): Int

    // Limpeza periódica de tokens expirados (agendado)
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revokedAt IS NOT NULL")
    fun deleteExpiredAndRevoked(now: Instant = Instant.now()): Int
}
