package br.com.finflow.dashboard.dto

import java.math.BigDecimal
import java.time.LocalDate

data class DashboardSummaryDto(
    val period: PeriodDto,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netBalance: BigDecimal,
    val byCategory: List<CategoryBreakdownDto>,
    val monthlyTrend: List<MonthlyTrendDto>
)

data class PeriodDto(val start: LocalDate, val end: LocalDate)

data class CategoryBreakdownDto(
    val categoryId: String?,
    val categoryName: String,
    val total: BigDecimal,
    val percentage: Double,
    val color: String?
)

data class MonthlyTrendDto(
    val month: String,       // "2026-01"
    val income: BigDecimal,
    val expenses: BigDecimal
)
