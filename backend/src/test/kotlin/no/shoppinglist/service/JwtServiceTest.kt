package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.JwtConfig
import java.util.UUID

class JwtServiceTest :
    FunSpec({
        val jwtConfig =
            JwtConfig(
                secret = "test-secret-key-at-least-32-chars-long",
                issuer = "test-issuer",
                audience = "test-audience",
                realm = "test-realm",
                expirationMinutes = 24,
            )
        val jwtService = JwtService(jwtConfig)

        test("generates valid token for account") {
            val accountId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateToken(accountId, email)

            token shouldNotBe null
            token.isNotEmpty() shouldBe true
        }

        test("validates and extracts account ID from token") {
            val accountId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateToken(accountId, email)
            val extractedId = jwtService.validateAndGetAccountId(token)

            extractedId shouldBe accountId
        }

        test("returns null for invalid token") {
            val result = jwtService.validateAndGetAccountId("invalid-token")

            result shouldBe null
        }

        test("returns null for expired token") {
            val expiredConfig = jwtConfig.copy(expirationMinutes = -1)
            val expiredJwtService = JwtService(expiredConfig)
            val accountId = UUID.randomUUID()

            val token = expiredJwtService.generateToken(accountId, "test@example.com")
            val result = jwtService.validateAndGetAccountId(token)

            result shouldBe null
        }
    })
