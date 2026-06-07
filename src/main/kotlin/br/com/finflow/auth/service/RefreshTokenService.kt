package br.com.finflow.auth.service

import br.com.finflow.auth.model.RefreshToken
import br.com.finflow.auth.model.User
import br.com.finflow.auth.repository.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${app.jwt.refresh-token-expiration}") private val expirationSeconds: Long
) {

    @Transactional
    fun create(user: User, userAgent: String?): String {
        // Gera token aleatório e armazena somente o hash (segurança: DB leak não expõe tokens)
        val rawToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val tokenHash = sha256(rawToken)

        refreshTokenRepository.save(
            RefreshToken(
                tokenHash = tokenHash,
                user = user,
                expiresAt = Instant.now().plusSeconds(expirationSeconds),
                userAgent = userAgent
            )
        )
        return rawToken
    }

    @Transactional
    fun rotate(rawToken: String, userAgent: String?): Pair<User, String> {
        val hash = sha256(rawToken)
        val stored = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { IllegalArgumentException("Refresh token inválido") }

        check(stored.isValid) { "Refresh token expirado ou revogado" }

        // Revoga token atual (rotação — cada token só pode ser usado uma vez)
        stored.revokedAt = Instant.now()
        refreshTokenRepository.save(stored)

        // Emite novo token
        val newRaw = create(stored.user, userAgent)
        return stored.user to newRaw
    }

    @Transactional
    fun revokeAll(user: User) {
        refreshTokenRepository.revokeAllByUser(user)
    }

    // Limpeza automática de tokens expirados — roda toda madrugada
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupExpired() {
        val deleted = refreshTokenRepository.deleteExpiredAndRevoked()
        if (deleted > 0) println("[RefreshTokenService] Removidos $deleted tokens expirados/revogados")
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }
}
