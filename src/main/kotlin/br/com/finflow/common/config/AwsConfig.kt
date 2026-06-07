package br.com.finflow.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * AwsConfig — configura os clientes AWS SDK v2.
 *
 * Em desenvolvimento local:
 * - S3 aponta para MinIO via endpoint customizado (app.aws.s3.endpoint)
 * - Credenciais estáticas para MinIO (app.aws.s3.access-key / secret-key)
 * - Bedrock não tem substituto local — use profile local e limite chamadas durante dev
 *
 * Em produção:
 * - Credenciais via IAM Role do ECS (DefaultCredentialsProvider detecta automaticamente)
 * - Sem endpoint customizado
 *
 * ⚠️ Lembre de desligar o ECS e RDS quando não estiver usando para evitar custos desnecessários.
 */
@Configuration
class AwsConfig(
    @Value("\${app.aws.region}") private val region: String,
    @Value("\${app.aws.s3.endpoint:}") private val s3Endpoint: String,
    @Value("\${app.aws.s3.access-key:}") private val s3AccessKey: String,
    @Value("\${app.aws.s3.secret-key:}") private val s3SecretKey: String
) {

    private fun s3CredentialsProvider() =
        if (s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank())
            StaticCredentialsProvider.create(AwsBasicCredentials.create(s3AccessKey, s3SecretKey))
        else
            DefaultCredentialsProvider.create()

    @Bean
    fun s3Client(): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(s3CredentialsProvider())

        // Em dev: redireciona para MinIO local
        if (s3Endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(s3Endpoint))
                .forcePathStyle(true)  // MinIO exige path-style (bucket no path, não no host)
        }

        return builder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val builder = S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(s3CredentialsProvider())

        if (s3Endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(s3Endpoint))
        }

        return builder.build()
    }

    @Bean
    fun bedrockRuntimeClient(): BedrockRuntimeClient =
        BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
}
