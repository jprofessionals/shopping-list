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
    val household = reference("household_id", Households)
    val name = varchar("name", 255)
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val frequency = enumerationByName<RecurringFrequency>("frequency", 20)
    val lastPurchased = date("last_purchased").nullable()
    val isActive = bool("is_active").default(true)
    val pausedUntil = date("paused_until").nullable()
    val createdBy = reference("created_by_id", Accounts)
}

class RecurringItem(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RecurringItem>(RecurringItems)

    var household by Household referencedOn RecurringItems.household
    var name by RecurringItems.name
    var quantity by RecurringItems.quantity
    var unit by RecurringItems.unit
    var frequency by RecurringItems.frequency
    var lastPurchased by RecurringItems.lastPurchased
    var isActive by RecurringItems.isActive
    var pausedUntil by RecurringItems.pausedUntil
    var createdBy by Account referencedOn RecurringItems.createdBy
}
