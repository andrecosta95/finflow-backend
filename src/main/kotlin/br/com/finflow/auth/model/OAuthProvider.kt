package br.com.finflow.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "oauth_providers",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])]
)
data class OAuthProvider(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    // "google", "facebook", "apple"
    @Column(nullable = false)
    val provider: String,

    // ID único do usuário no provedor OAuth
    @Column(name = "provider_id", nullable = false)
    val providerId: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    constructor() : this(user = User(), provider = "", providerId = "")
}
