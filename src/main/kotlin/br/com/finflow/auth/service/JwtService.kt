package br.com.finflow.auth.service

import br.com.finflow.auth.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * JwtService — gera e valida JWTs assinados com RS256 (chave assimétrica).
 *
 * Por que RS256 em vez de HS256?
 * - Com RS256 o frontend pode verificar a assinatura sem precisar da chave privada.
 * - Permite que outros serviços validem tokens sem compartilhar segredo.
 * - Chave privada fica SOMENTE no backend.
 */
@Service
class JwtService(
    @Value("\${app.jwt.private-key}") private val privateKeyResource: Resource,
    @Value("\${app.jwt.public-key}") private val publicKeyResource: Resource,
    @Value("\${app.jwt.access-token-expiration}") private val accessTokenExpiration: Long
) {

    private val privateKey: PrivateKey by lazy { loadPrivateKey() }
    val publicKey: PublicKey by lazy { loadPublicKey() }

    fun generateAccessToken(user: User): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("name", user.name)
            .claim("plan", user.plan.name)
            .issuedAt(Date(now))
            .expiration(Date(now + accessTokenExpiration * 1000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }

    fun extractClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .payload

    fun extractUserId(token: String): UUID =
        UUID.fromString(extractClaims(token).subject)

    fun isTokenValid(token: String): Boolean = runCatching {
        extractClaims(token).expiration.after(Date())
    }.getOrDefault(false)

    // ── Carregamento das chaves PEM ──────────────────────────────────────

    private fun loadPrivateKey(): PrivateKey {
        val content = privateKeyResource.inputStream.readBytes().toString(Charsets.UTF_8)
        val pem = content
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(pem)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun loadPublicKey(): PublicKey {
        val content = publicKeyResource.inputStream.readBytes().toString(Charsets.UTF_8)
        val pem = content
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(pem)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    }
}
