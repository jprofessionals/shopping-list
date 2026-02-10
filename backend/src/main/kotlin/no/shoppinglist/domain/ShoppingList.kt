package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object ShoppingLists : UUIDTable("shopping_lists") {
    val name = varchar("name", 255)
    val owner = reference("owner_id", Accounts)
    val household = reference("household_id", Households).nullable()
    val isPersonal = bool("is_personal").default(false)
    val createdAt = timestamp("created_at")
}

class ShoppingList(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ShoppingList>(ShoppingLists)

    var name by ShoppingLists.name
    var owner by Account referencedOn ShoppingLists.owner
    var household by Household optionalReferencedOn ShoppingLists.household
    var isPersonal by ShoppingLists.isPersonal
    var createdAt by ShoppingLists.createdAt
}
