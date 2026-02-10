package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object ItemHistories : UUIDTable("item_history") {
    val account = reference("account_id", Accounts)
    val name = varchar("name", 255) // normalized lowercase
    val displayName = varchar("display_name", 255)
    val typicalQuantity = double("typical_quantity").default(1.0)
    val typicalUnit = varchar("typical_unit", 50).nullable()
    val useCount = integer("use_count").default(1)
    val lastUsedAt = timestamp("last_used_at")

    init {
        uniqueIndex(account, name)
    }
}

class ItemHistory(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ItemHistory>(ItemHistories)

    var account by Account referencedOn ItemHistories.account
    var name by ItemHistories.name
    var displayName by ItemHistories.displayName
    var typicalQuantity by ItemHistories.typicalQuantity
    var typicalUnit by ItemHistories.typicalUnit
    var useCount by ItemHistories.useCount
    var lastUsedAt by ItemHistories.lastUsedAt
}
