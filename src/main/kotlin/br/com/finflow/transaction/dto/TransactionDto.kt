package br.com.finflow.transaction.dto

import br.com.finflow.transaction.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class TransactionDto(
    val id: UUID,
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val type: String,
    val categoryId: UUID?,
    val categoryName: String?,
    val categoryConfirmed: Boolean?
) {
    companion object {
        fun from(t: Transaction) = TransactionDto(
            id = t.id,
            date = t.transactionDate,
            description = t.description,
            amount = t.amount,
            type = t.type.name,
            categoryId = t.category?.id,
            categoryName = t.category?.name,
            categoryConfirmed = t.categoryConfirmed
        )
    }
}

data class UpdateCategoryRequest(val categoryId: UUID)
