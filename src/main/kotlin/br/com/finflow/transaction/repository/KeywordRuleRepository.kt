package br.com.finflow.transaction.repository

import br.com.finflow.transaction.model.KeywordRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KeywordRuleRepository : JpaRepository<KeywordRule, UUID> {
    fun findByUserId(userId: UUID): List<KeywordRule>
}
