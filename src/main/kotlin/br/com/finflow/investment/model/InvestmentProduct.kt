package br.com.finflow.investment.model

import br.com.finflow.auth.model.User
import br.com.finflow.document.model.Document
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "investment_products")
data class InvestmentProduct(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    val document: Document? = null,

    @Column(nullable = false)
    val bank: String,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    val productType: ProductType,

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    val currentBalance: BigDecimal,

    @Column(name = "invested_amount", precision = 15, scale = 2)
    val investedAmount: BigDecimal? = null,

    @Column(name = "profitability_pct", precision = 8, scale = 4)
    val profitabilityPct: BigDecimal? = null,

    @Column(name = "reference_date", nullable = false)
    val referenceDate: LocalDate,

    @Column(name = "maturity_date")
    val maturityDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

) {
    constructor() : this(
        user = User(), bank = "", productName = "",
        productType = ProductType.OTHER, currentBalance = BigDecimal.ZERO,
        referenceDate = LocalDate.now()
    )

    enum class ProductType {
        CDB, LCI, LCA, PGBL, VGBL, FI, STOCKS, ETF, TREASURY, OTHER
    }
}
