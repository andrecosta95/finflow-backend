package br.com.finflow.dashboard.service

import br.com.finflow.dashboard.dto.*
import br.com.finflow.transaction.model.Transaction
import br.com.finflow.transaction.repository.TransactionRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class DashboardService(
    private val transactionRepository: TransactionRepository
) {

    /**
     * Resumo financeiro do período.
     *
     * Cacheado por 5 minutos no Redis (alinhado com staleTime do TanStack Query no frontend).
     * A chave inclui userId + período para isolar dados entre usuários.
     *
     * ⚠️ Lembre-se: se o Redis não estiver rodando localmente o app falha.
     * Execute: docker-compose up redis -d
     */
    @Cacheable(value = ["dashboard"], key = "#userId + ':' + #start + ':' + #end")
    fun getSummary(userId: UUID, start: LocalDate, end: LocalDate): DashboardSummaryDto {
        val transactions = transactionRepository.findByUserAndPeriod(userId, start, end)

        val totalIncome = transactions
            .filter { it.type == Transaction.Type.INCOME }
            .sumOf { it.amount }

        val totalExpenses = transactions
            .filter { it.type == Transaction.Type.EXPENSE }
            .sumOf { it.amount }

        return DashboardSummaryDto(
            period = PeriodDto(start.toString(), end.toString()),
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netBalance = totalIncome - totalExpenses,
            byCategory = buildCategoryBreakdown(transactions),
            monthlyTrend = buildMonthlyTrend(userId, start, end)
        )
    }

    private fun buildCategoryBreakdown(transactions: List<Transaction>): List<CategoryBreakdownDto> {
        val expenses = transactions.filter { it.type == Transaction.Type.EXPENSE }
        val totalExpenses = expenses.sumOf { it.amount }
        if (totalExpenses == BigDecimal.ZERO) return emptyList()

        return expenses
            .groupBy { it.category }
            .map { (category, txs) ->
                val total = txs.sumOf { it.amount }
                CategoryBreakdownDto(
                    categoryId = category?.id?.toString(),
                    categoryName = category?.name ?: "Sem categoria",
                    total = total,
                    percentage = total.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal("100"))
                        .toDouble(),
                    color = category?.color
                )
            }
            .sortedByDescending { it.total }
    }

    private fun buildMonthlyTrend(userId: UUID, start: LocalDate, end: LocalDate): List<MonthlyTrendDto> {
        // Expande a janela para 6 meses anteriores para mostrar tendência
        val trendStart = start.minusMonths(5).withDayOfMonth(1)
        val transactions = transactionRepository.findByUserAndPeriod(userId, trendStart, end)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

        return transactions
            .groupBy { it.transactionDate.format(formatter) }
            .map { (month, txs) ->
                MonthlyTrendDto(
                    month = month,
                    income = txs.filter { it.type == Transaction.Type.INCOME }.sumOf { it.amount },
                    expenses = txs.filter { it.type == Transaction.Type.EXPENSE }.sumOf { it.amount }
                )
            }
            .sortedBy { it.month }
    }
}
