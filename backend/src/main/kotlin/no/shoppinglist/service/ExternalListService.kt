package no.shoppinglist.service

import kotlinx.serialization.Serializable
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShareType
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Serializable
data class ExternalItemRequest(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
)

data class ExternalListResult(
    val listId: UUID,
    val shareToken: String,
)

class ExternalListService(private val db: Database) {

    private val random = SecureRandom()

    fun createExternalList(
        title: String,
        email: String?,
        items: List<ExternalItemRequest>,
    ): ExternalListResult = transaction(db) {
        val shareToken = generateToken()

        val list = ShoppingList.new {
            this.name = title
            this.pendingEmail = email
            this.createdAt = Instant.now()
        }

        ListShare.new {
            this.list = list
            this.type = ShareType.LINK
            this.permission = SharePermission.WRITE
            this.linkToken = shareToken
            this.createdAt = Instant.now()
        }

        items.forEach { item ->
            ListItem.new {
                this.list = list
                this.name = item.name
                this.quantity = item.quantity ?: 1.0
                this.unit = item.unit
                this.isChecked = false
                this.createdAt = Instant.now()
                this.updatedAt = Instant.now()
            }
        }

        ExternalListResult(
            listId = list.id.value,
            shareToken = shareToken,
        )
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
