package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.RefreshTokens
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class RefreshTokenServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var refreshTokenService: RefreshTokenService
        lateinit var testAccountId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, RefreshTokens)
            }
            refreshTokenService = RefreshTokenService(db)

            testAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(testAccountId) {
                    email = "refresh-test-${System.nanoTime()}@example.com"
                    displayName = "Refresh Test User"
                    createdAt = Instant.now()
                }
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(RefreshTokens, Accounts)
            }
        }

        test("createToken and validateAndGetAccountId work correctly") {
            val rawToken = refreshTokenService.createToken(testAccountId)
            rawToken shouldNotBe null

            val accountId = refreshTokenService.validateAndGetAccountId(rawToken)
            accountId shouldBe testAccountId
        }

        test("rotateToken deletes old and creates new") {
            val oldToken = refreshTokenService.createToken(testAccountId)
            val newToken = refreshTokenService.rotateToken(oldToken, testAccountId)

            newToken shouldNotBe null
            newToken shouldNotBe oldToken

            // Old token should no longer be valid
            refreshTokenService.validateAndGetAccountId(oldToken) shouldBe null

            // New token should be valid
            refreshTokenService.validateAndGetAccountId(newToken!!) shouldBe testAccountId
        }

        test("validateAndGetAccountId returns null for expired token") {
            // Create a token, then manually expire it
            val rawToken = refreshTokenService.createToken(testAccountId)
            val hash = RefreshTokenService.hashToken(rawToken)

            transaction(db) {
                val token =
                    no.shoppinglist.domain.RefreshToken
                        .find { RefreshTokens.tokenHash eq hash }
                        .first()
                token.expiresAt = Instant.now().minusSeconds(1)
            }

            refreshTokenService.validateAndGetAccountId(rawToken) shouldBe null
        }

        test("cleanupExpired removes expired tokens") {
            val rawToken = refreshTokenService.createToken(testAccountId)
            val hash = RefreshTokenService.hashToken(rawToken)

            // Manually expire the token
            transaction(db) {
                val token =
                    no.shoppinglist.domain.RefreshToken
                        .find { RefreshTokens.tokenHash eq hash }
                        .first()
                token.expiresAt = Instant.now().minusSeconds(1)
            }

            refreshTokenService.cleanupExpired()

            // Token should be gone
            transaction(db) {
                no.shoppinglist.domain.RefreshToken
                    .find { RefreshTokens.tokenHash eq hash }
                    .count() shouldBe 0
            }
        }

        test("deleteByToken removes specific token") {
            val rawToken = refreshTokenService.createToken(testAccountId)
            refreshTokenService.validateAndGetAccountId(rawToken) shouldBe testAccountId

            refreshTokenService.deleteByToken(rawToken)
            refreshTokenService.validateAndGetAccountId(rawToken) shouldBe null
        }

        test("deleteAllForAccount removes all tokens for account") {
            val deleteAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(deleteAccountId) {
                    email = "delete-all-${System.nanoTime()}@example.com"
                    displayName = "Delete All Test User"
                    createdAt = Instant.now()
                }
            }

            val token1 = refreshTokenService.createToken(deleteAccountId)
            val token2 = refreshTokenService.createToken(deleteAccountId)

            refreshTokenService.validateAndGetAccountId(token1) shouldBe deleteAccountId
            refreshTokenService.validateAndGetAccountId(token2) shouldBe deleteAccountId

            refreshTokenService.deleteAllForAccount(deleteAccountId)

            refreshTokenService.validateAndGetAccountId(token1) shouldBe null
            refreshTokenService.validateAndGetAccountId(token2) shouldBe null
        }
    })
