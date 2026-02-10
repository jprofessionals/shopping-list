package no.shoppinglist.service

import at.favre.lib.crypto.bcrypt.BCrypt
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountService(
    private val db: Database,
) {
    fun findByEmail(email: String): Account? =
        transaction(db) {
            Account.find { Accounts.email eq email }.firstOrNull()
        }

    fun findByGoogleId(googleId: String): Account? =
        transaction(db) {
            Account.find { Accounts.googleId eq googleId }.firstOrNull()
        }

    fun findById(id: UUID): Account? =
        transaction(db) {
            Account.findById(id)
        }

    fun createFromGoogle(
        googleId: String,
        email: String,
        displayName: String,
        avatarUrl: String?,
    ): Account =
        transaction(db) {
            Account.new {
                this.email = email
                this.displayName = displayName
                this.googleId = googleId
                this.avatarUrl = avatarUrl
                this.createdAt = Instant.now()
            }
        }

    fun createLocal(
        email: String,
        displayName: String,
        password: String,
    ): Account {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(db) {
            Account.new {
                this.email = email
                this.displayName = displayName
                this.passwordHash = hash
                this.createdAt = Instant.now()
            }
        }
    }

    fun verifyPassword(
        account: Account,
        password: String,
    ): Boolean {
        val hash = transaction(db) { account.passwordHash } ?: return false
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    fun linkGoogleAccount(
        account: Account,
        googleId: String,
        avatarUrl: String?,
    ) {
        transaction(db) {
            account.googleId = googleId
            if (avatarUrl != null && account.avatarUrl == null) {
                account.avatarUrl = avatarUrl
            }
        }
    }
}
