package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object ListActivities : UUIDTable("list_activity") {
    val list = reference("list_id", ShoppingLists)
    val account = reference("account_id", Accounts)
    val actionType = varchar("action_type", 50)
    val targetName = varchar("target_name", 255).nullable()
    val createdAt = timestamp("created_at")
}

class ListActivity(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListActivity>(ListActivities)

    var list by ShoppingList referencedOn ListActivities.list
    var account by Account referencedOn ListActivities.account
    var actionType by ListActivities.actionType
    var targetName by ListActivities.targetName
    var createdAt by ListActivities.createdAt
}

enum class ActivityType(
    val value: String,
) {
    ITEM_ADDED("item_added"),
    ITEM_UPDATED("item_updated"),
    ITEM_CHECKED("item_checked"),
    ITEM_UNCHECKED("item_unchecked"),
    ITEM_REMOVED("item_removed"),
    LIST_CREATED("list_created"),
    LIST_UPDATED("list_updated"),
}
