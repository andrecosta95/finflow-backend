package br.com.finflow.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    // Hash SHA-256 do token — nunca armazenamos o valor raw
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    // Para rastreabilidade — qual dispositivo/sessão gerou este token
    @Column(name = "user_agent")
    val userAgent: String? = null
) {
    constructor() : this(tokenHash = "", user = User(), expiresAt = Instant.now())

    val isValid: Boolean
        get() = revokedAt == null && Instant.now().isBefore(expiresAt)
}
