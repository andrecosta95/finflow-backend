package br.com.finflow.document.repository

import br.com.finflow.auth.model.User
import br.com.finflow.document.model.Document
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByUserOrderByCreatedAtDesc(user: User, pageable: Pageable): Page<Document>
    fun findByUserAndStatus(user: User, status: Document.Status): List<Document>
}
