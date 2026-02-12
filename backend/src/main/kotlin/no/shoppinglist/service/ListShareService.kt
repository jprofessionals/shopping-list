package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShareType
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ListShareService(
    private val db: Database,
) {
    private val secureRandom = SecureRandom()
    private val tokenChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun createUserShare(
        listId: UUID,
        accountId: UUID,
        permission: SharePermission,
    ): ListShare =
        transaction(db) {
            val list =
                ShoppingList.findById(listId)
                    ?: throw IllegalArgumentException("List not found: $listId")
            val account =
                Account.findById(accountId)
                    ?: throw IllegalArgumentException("Account not found: $accountId")

            ListShare.new {
                this.list = list
                this.type = ShareType.USER
                this.account = account
                this.linkToken = null
                this.permission = permission
                this.expiresAt = null
                this.createdAt = Instant.now()
            }
        }

    fun createLinkShare(
        listId: UUID,
        permission: SharePermission,
        expirationHours: Int,
    ): ListShare =
        transaction(db) {
            val list =
                ShoppingList.findById(listId)
                    ?: throw IllegalArgumentException("List not found: $listId")

            ListShare.new {
                this.list = list
                this.type = ShareType.LINK
                this.account = null
                this.linkToken = generateToken()
                this.permission = permission
                this.expiresAt = Instant.now().plus(expirationHours.toLong(), ChronoUnit.HOURS)
                this.createdAt = Instant.now()
            }
        }

    fun findByListId(listId: UUID): List<ListShare> =
        transaction(db) {
            ListShare.find { ListShares.list eq listId }.toList()
        }

    fun findById(id: UUID): ListShare? =
        transaction(db) {
            ListShare.findById(id)
        }

    fun findByToken(token: String): ListShare? =
        transaction(db) {
            val share =
                ListShare
                    .find { (ListShares.linkToken eq token) and (ListShares.type eq ShareType.LINK) }
                    .firstOrNull() ?: return@transaction null

            if (share.expiresAt != null && share.expiresAt!! <= Instant.now()) {
                return@transaction null
            }
            share
        }

    fun isTokenExpired(token: String): Boolean =
        transaction(db) {
            val share =
                ListShare
                    .find { (ListShares.linkToken eq token) and (ListShares.type eq ShareType.LINK) }
                    .firstOrNull() ?: return@transaction false

            share.expiresAt != null && share.expiresAt!! <= Instant.now()
        }

    fun update(
        id: UUID,
        permission: SharePermission,
        expiresAt: Instant?,
    ): ListShare? =
        transaction(db) {
            val share = ListShare.findById(id) ?: return@transaction null
            share.permission = permission
            if (share.type == ShareType.LINK) {
                share.expiresAt = expiresAt
            }
            share
        }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            val deleted = ListShares.deleteWhere { ListShares.id eq id }
            deleted > 0
        }

    private fun generateToken(): String {
        val sb = StringBuilder(32)
        repeat(32) {
            sb.append(tokenChars[secureRandom.nextInt(tokenChars.length)])
        }
        return sb.toString()
    }
}
