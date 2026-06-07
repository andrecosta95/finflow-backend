package br.com.finflow.transaction.model

import br.com.finflow.auth.model.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "categories")
data class Category(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    // NULL = categoria global do sistema
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: Transaction.Type,

    val icon: String? = null,
    val color: String? = null,

    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    constructor() : this(name = "", type = Transaction.Type.EXPENSE)
}
