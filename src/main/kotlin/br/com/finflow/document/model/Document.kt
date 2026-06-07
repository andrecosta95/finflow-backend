package br.com.finflow.document.model

import br.com.finflow.auth.model.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "documents")
data class Document(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "file_name", nullable = false)
    val fileName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    val fileType: FileType,

    @Column(name = "s3_key", nullable = false)
    val s3Key: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.PENDING,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "parsed_at")
    var parsedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    constructor() : this(user = User(), fileName = "", fileType = FileType.PDF, s3Key = "")

    enum class FileType { PDF, XLSX, CSV, IMAGE }

    enum class Status { PENDING, PROCESSING, DONE, ERROR }
}
