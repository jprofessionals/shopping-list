package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.date
import java.util.UUID

enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
}

object RecurringItems : UUIDTable("recurring_items") {
    val list = reference("list_id", ShoppingLists)
    val name = varchar("name", 255)
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val frequency = enumerationByName<RecurringFrequency>("frequency", 20)
    val nextOccurrence = date("next_occurrence")
    val isActive = bool("is_active").default(true)
    val createdBy = reference("created_by_id", Accounts)
}

class RecurringItem(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RecurringItem>(RecurringItems)

    var list by ShoppingList referencedOn RecurringItems.list
    var name by RecurringItems.name
    var quantity by RecurringItems.quantity
    var unit by RecurringItems.unit
    var frequency by RecurringItems.frequency
    var nextOccurrence by RecurringItems.nextOccurrence
    var isActive by RecurringItems.isActive
    var createdBy by Account referencedOn RecurringItems.createdBy
}
