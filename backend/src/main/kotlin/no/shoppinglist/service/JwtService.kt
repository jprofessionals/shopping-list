package no.shoppinglist.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import no.shoppinglist.config.JwtConfig
import java.util.Date
import java.util.UUID

class JwtService(
    private val config: JwtConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    private val verifier =
        JWT
            .require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()

    fun generateToken(
        accountId: UUID,
        email: String,
    ): String {
        val now = System.currentTimeMillis()
        val expiration = now + (config.expirationMinutes * 60 * 1000L)

        return JWT
            .create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(accountId.toString())
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiration))
            .sign(algorithm)
    }

    @Suppress("SwallowedException")
    fun validateAndGetAccountId(token: String): UUID? =
        try {
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (e: JWTVerificationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    @Suppress("SwallowedException")
    fun decode(token: String): DecodedJWT? =
        try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    fun getExpirationMinutes(): Int = config.expirationMinutes

    fun getAlgorithm(): Algorithm = algorithm
}
