package no.shoppinglist.service

import no.shoppinglist.domain.RefreshToken
import no.shoppinglist.domain.RefreshTokens
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class RefreshTokenService(
    private val db: Database,
) {
    companion object {
        private const val REFRESH_TOKEN_DAYS = 30L

        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    fun createToken(accountId: UUID): String =
        transaction(db) {
            val rawToken = UUID.randomUUID().toString()
            RefreshToken.new {
                this.accountId =
                    org.jetbrains.exposed.dao.id
                        .EntityID(accountId, no.shoppinglist.domain.Accounts)
                this.tokenHash = hashToken(rawToken)
                this.expiresAt = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)
                this.createdAt = Instant.now()
            }
            rawToken
        }

    fun validateAndGetAccountId(rawToken: String): UUID? =
        transaction(db) {
            val hash = hashToken(rawToken)
            val token =
                RefreshToken
                    .find {
                        (RefreshTokens.tokenHash eq hash) and
                            (RefreshTokens.expiresAt greater Instant.now())
                    }.firstOrNull()
            token?.accountId?.value
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun rotateToken(
        oldRawToken: String,
        accountId: UUID,
    ): String? =
        try {
            transaction(db) {
                val hash = hashToken(oldRawToken)
                val existing =
                    RefreshToken
                        .find {
                            (RefreshTokens.tokenHash eq hash) and
                                (RefreshTokens.expiresAt greater Instant.now())
                        }.firstOrNull()

                if (existing == null || existing.accountId.value != accountId) {
                    return@transaction null
                }

                existing.delete()

                val newRawToken = UUID.randomUUID().toString()
                RefreshToken.new {
                    this.accountId =
                        org.jetbrains.exposed.dao.id
                            .EntityID(accountId, no.shoppinglist.domain.Accounts)
                    this.tokenHash = hashToken(newRawToken)
                    this.expiresAt = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)
                    this.createdAt = Instant.now()
                }
                newRawToken
            }
        } catch (e: Exception) {
            // Concurrent token rotation (e.g., two devices refreshing simultaneously)
            // returns null which triggers a 401, forcing re-authentication
            null
        }

    fun deleteByToken(rawToken: String) {
        transaction(db) {
            val hash = hashToken(rawToken)
            RefreshTokens.deleteWhere { tokenHash eq hash }
        }
    }

    fun deleteAllForAccount(accountId: UUID) {
        transaction(db) {
            RefreshTokens.deleteWhere {
                RefreshTokens.accountId eq
                    org.jetbrains.exposed.dao.id.EntityID(
                        accountId,
                        no.shoppinglist.domain.Accounts,
                    )
            }
        }
    }

    fun cleanupExpired() {
        transaction(db) {
            RefreshTokens.deleteWhere { expiresAt less Instant.now() }
        }
    }
}
