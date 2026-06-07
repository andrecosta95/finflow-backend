package br.com.finflow.transaction.repository

import br.com.finflow.transaction.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface TransactionRepository : JpaRepository<Transaction, UUID> {

    // Campos mínimos para deduplicação — evita carregar objetos completos
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId")
    fun findByUserIdForDeduplication(userId: UUID): List<Transaction>

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate BETWEEN :start AND :end
        ORDER BY t.transactionDate DESC
    """)
    fun findByUserAndPeriod(userId: UUID, start: LocalDate, end: LocalDate): List<Transaction>

    // Soma por tipo no período — usado no dashboard
    @Query("""
        SELECT t.type, SUM(t.amount)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate BETWEEN :start AND :end
        GROUP BY t.type
    """)
    fun sumByTypeAndPeriod(userId: UUID, start: LocalDate, end: LocalDate): List<Array<Any>>

    // Transações sem categoria (para o job de categorização)
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.category IS NULL")
    fun findUncategorized(userId: UUID): List<Transaction>
}
