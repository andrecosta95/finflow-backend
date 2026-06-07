package br.com.finflow.transaction.repository

import br.com.finflow.transaction.model.Category
import br.com.finflow.transaction.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {

    // Retorna categorias do sistema + personalizadas do usuário
    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId ORDER BY c.name")
    fun findAllForUser(userId: UUID): List<Category>

    fun findByNameIgnoreCaseAndUserIsNull(name: String): Category?
}
