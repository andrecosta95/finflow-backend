package br.com.finflow.dashboard.dto

import java.math.BigDecimal

data class DashboardSummaryDto(
    val period: PeriodDto,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netBalance: BigDecimal,
    val byCategory: List<CategoryBreakdownDto>,
    val monthlyTrend: List<MonthlyTrendDto>
)

// Strings ISO (yyyy-MM-dd) — evita dependência de JavaTimeModule no ObjectMapper
data class PeriodDto(val start: String, val end: String)

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
