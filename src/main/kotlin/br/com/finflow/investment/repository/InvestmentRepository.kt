package br.com.finflow.investment.repository

import br.com.finflow.investment.model.InvestmentProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface InvestmentRepository : JpaRepository<InvestmentProduct, UUID> {

    fun findByUserIdOrderByCurrentBalanceDesc(userId: UUID): List<InvestmentProduct>

    // Total investido por banco — para gráfico de alocação
    @Query("""
        SELECT ip.bank, SUM(ip.currentBalance)
        FROM InvestmentProduct ip
        WHERE ip.user.id = :userId
        GROUP BY ip.bank
        ORDER BY SUM(ip.currentBalance) DESC
    """)
    fun sumByBank(userId: UUID): List<Array<Any>>

    // Total por tipo de produto — para gráfico de diversificação
    @Query("""
        SELECT ip.productType, SUM(ip.currentBalance)
        FROM InvestmentProduct ip
        WHERE ip.user.id = :userId
        GROUP BY ip.productType
    """)
    fun sumByProductType(userId: UUID): List<Array<Any>>
}
