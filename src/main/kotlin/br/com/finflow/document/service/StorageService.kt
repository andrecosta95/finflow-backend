package br.com.finflow.document.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * StorageService — abstrai S3/MinIO.
 * Em dev usa MinIO (endpoint local). Em prod usa S3 real.
 * A troca é transparente pois ambos implementam a API S3.
 */
@Service
class StorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${app.aws.s3.bucket}") private val bucket: String
) {

    fun upload(userId: UUID, originalFileName: String, bytes: ByteArray, contentType: String): String {
        val extension = originalFileName.substringAfterLast('.', "bin")
        val s3Key = "users/$userId/documents/${UUID.randomUUID()}.$extension"

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(bytes.size.toLong())
                .build(),
            RequestBody.fromBytes(bytes)
        )

        return s3Key
    }

    fun download(s3Key: String): ByteArray =
        s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(s3Key).build()
        ).readAllBytes()

    /**
     * Gera URL pré-assinada para download direto (válida por 15 minutos).
     * O frontend usa essa URL para mostrar o documento ao usuário sem expor credenciais AWS.
     */
    fun generatePresignedUrl(s3Key: String): String =
        s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest { it.bucket(bucket).key(s3Key) }
                .build()
        ).url().toString()

    fun delete(s3Key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build()
        )
    }
}
