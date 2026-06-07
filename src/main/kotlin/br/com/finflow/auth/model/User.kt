package br.com.finflow.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
data class User(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "avatar_url")
    val avatarUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val plan: Plan = Plan.FREE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Relacionamento com provedores OAuth (Google, Facebook, Apple)
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val oauthProviders: MutableList<OAuthProvider> = mutableListOf()
) {
    // JPA exige construtor sem argumentos
    constructor() : this(email = "", name = "")

    enum class Plan { FREE, PREMIUM }
}
