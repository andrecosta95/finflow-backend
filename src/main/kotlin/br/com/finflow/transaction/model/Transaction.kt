package br.com.finflow.transaction.model

import br.com.finflow.auth.model.User
import br.com.finflow.document.model.Document
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "transactions")
data class Transaction(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    val document: Document? = null,

    @Column(name = "transaction_date", nullable = false)
    val transactionDate: LocalDate,

    @Column(nullable = false)
    val description: String,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: Type,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    // NULL = categorizado por IA (pendente confirmação)
    // TRUE = usuário confirmou
    // FALSE = usuário corrigiu
    @Column(name = "category_confirmed")
    var categoryConfirmed: Boolean? = null,

    @Column
    var notes: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

) {
    constructor() : this(
        user = User(), transactionDate = LocalDate.now(),
        description = "", amount = BigDecimal.ZERO, type = Type.EXPENSE
    )

    enum class Type { INCOME, EXPENSE }
}
