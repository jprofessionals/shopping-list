package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object UserPreferencesTable : UUIDTable("user_preferences") {
    val account = reference("account_id", Accounts).uniqueIndex()
    val smartParsingEnabled = bool("smart_parsing_enabled").default(true)
    val defaultQuantity = double("default_quantity").default(1.0)
    val theme = varchar("theme", 20).default("system")
    val notifyNewList = bool("notify_new_list").default(true)
    val notifyItemAdded = bool("notify_item_added").default(true)
    val notifyNewComment = bool("notify_new_comment").default(true)
    val updatedAt = timestamp("updated_at")
}

class UserPreferences(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserPreferences>(UserPreferencesTable)

    var account by Account referencedOn UserPreferencesTable.account
    var smartParsingEnabled by UserPreferencesTable.smartParsingEnabled
    var defaultQuantity by UserPreferencesTable.defaultQuantity
    var theme by UserPreferencesTable.theme
    var notifyNewList by UserPreferencesTable.notifyNewList
    var notifyItemAdded by UserPreferencesTable.notifyItemAdded
    var notifyNewComment by UserPreferencesTable.notifyNewComment
    var updatedAt by UserPreferencesTable.updatedAt
}
