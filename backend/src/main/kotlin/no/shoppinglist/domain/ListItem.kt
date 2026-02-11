package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object ListItems : UUIDTable("list_items") {
    val list = reference("list_id", ShoppingLists)
    val name = text("name")
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val barcode = varchar("barcode", 100).nullable()
    val isChecked = bool("is_checked").default(false)
    val checkedBy = reference("checked_by_id", Accounts).nullable()
    val createdBy = reference("created_by_id", Accounts)
    val recurringItem = reference("recurring_item_id", RecurringItems).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

class ListItem(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListItem>(ListItems)

    var list by ShoppingList referencedOn ListItems.list
    var name by ListItems.name
    var quantity by ListItems.quantity
    var unit by ListItems.unit
    var barcode by ListItems.barcode
    var isChecked by ListItems.isChecked
    var checkedBy by Account optionalReferencedOn ListItems.checkedBy
    var createdBy by Account referencedOn ListItems.createdBy
    var recurringItem by RecurringItem optionalReferencedOn ListItems.recurringItem
    var createdAt by ListItems.createdAt
    var updatedAt by ListItems.updatedAt
}
