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
    var updatedAt by UserPreferencesTable.updatedAt
}
