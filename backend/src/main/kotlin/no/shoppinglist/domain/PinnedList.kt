package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PinnedLists : UUIDTable("pinned_list") {
    val account = reference("account_id", Accounts)
    val list = reference("list_id", ShoppingLists)
    val pinnedAt = timestamp("pinned_at")

    init {
        uniqueIndex(account, list)
    }
}

class PinnedList(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PinnedList>(PinnedLists)

    var account by Account referencedOn PinnedLists.account
    var list by ShoppingList referencedOn PinnedLists.list
    var pinnedAt by PinnedLists.pinnedAt
}
