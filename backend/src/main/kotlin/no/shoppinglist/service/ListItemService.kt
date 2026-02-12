package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.RecurringItem
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListItemService(
    private val db: Database,
) {
    fun create(
        listId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
        barcode: String?,
        createdById: UUID,
    ): ListItem =
        transaction(db) {
            val list =
                ShoppingList.findById(listId)
                    ?: throw IllegalArgumentException("List not found: $listId")
            val createdBy =
                Account.findById(createdById)
                    ?: throw IllegalArgumentException("Account not found: $createdById")
            val now = Instant.now()

            ListItem.new {
                this.list = list
                this.name = name
                this.quantity = quantity
                this.unit = unit
                this.barcode = barcode
                this.isChecked = false
                this.checkedBy = null
                this.createdBy = createdBy
                this.createdAt = now
                this.updatedAt = now
            }
        }

    fun createFromRecurring(
        listId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
        createdById: UUID,
        recurringItemId: UUID,
    ): ListItem =
        transaction(db) {
            val list =
                ShoppingList.findById(listId)
                    ?: throw IllegalArgumentException("List not found: $listId")
            val createdBy =
                Account.findById(createdById)
                    ?: throw IllegalArgumentException("Account not found: $createdById")
            val recurring =
                RecurringItem.findById(recurringItemId)
                    ?: throw IllegalArgumentException("Recurring item not found: $recurringItemId")
            val now = Instant.now()

            ListItem.new {
                this.list = list
                this.name = name
                this.quantity = quantity
                this.unit = unit
                this.barcode = null
                this.isChecked = false
                this.checkedBy = null
                this.createdBy = createdBy
                this.recurringItem = recurring
                this.createdAt = now
                this.updatedAt = now
            }
        }

    fun findByListId(listId: UUID): List<ListItem> =
        transaction(db) {
            ListItem.find { ListItems.list eq listId }.toList()
        }

    fun findById(id: UUID): ListItem? =
        transaction(db) {
            ListItem.findById(id)
        }

    fun update(
        id: UUID,
        name: String,
        quantity: Double,
        unit: String?,
    ): ListItem? =
        transaction(db) {
            val item = ListItem.findById(id) ?: return@transaction null
            item.name = name
            item.quantity = quantity
            item.unit = unit
            item.updatedAt = Instant.now()
            item
        }

    fun toggleCheck(
        id: UUID,
        accountId: UUID,
    ): ListItem? =
        transaction(db) {
            val item = ListItem.findById(id) ?: return@transaction null
            val account = Account.findById(accountId)

            if (item.isChecked) {
                item.isChecked = false
                item.checkedBy = null
            } else {
                item.isChecked = true
                item.checkedBy = account
            }
            item.updatedAt = Instant.now()
            item
        }

    fun toggleCheckAnonymous(id: UUID): ListItem? =
        transaction(db) {
            val item = ListItem.findById(id) ?: return@transaction null
            item.isChecked = !item.isChecked
            item.checkedBy = null
            item.updatedAt = Instant.now()
            item
        }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            val deleted = ListItems.deleteWhere { ListItems.id eq id }
            deleted > 0
        }

    fun deleteCheckedItems(listId: UUID): List<UUID> =
        transaction(db) {
            val checkedItems =
                ListItem
                    .find {
                        (ListItems.list eq listId) and (ListItems.isChecked eq true)
                    }.toList()

            val deletedIds = checkedItems.map { it.id.value }
            checkedItems.forEach { it.delete() }
            deletedIds
        }
}
