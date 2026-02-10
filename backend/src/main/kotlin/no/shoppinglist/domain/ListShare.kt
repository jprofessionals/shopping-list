package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

enum class ShareType {
    USER,
    LINK,
}

enum class SharePermission {
    READ,
    CHECK,
    WRITE,
}

object ListShares : UUIDTable("list_shares") {
    val list = reference("list_id", ShoppingLists)
    val type = enumerationByName<ShareType>("type", 20)
    val account = reference("account_id", Accounts).nullable()
    val linkToken = varchar("link_token", 255).nullable().uniqueIndex()
    val permission = enumerationByName<SharePermission>("permission", 20)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at").nullable()
}

class ListShare(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListShare>(ListShares)

    var list by ShoppingList referencedOn ListShares.list
    var type by ListShares.type
    var account by Account optionalReferencedOn ListShares.account
    var linkToken by ListShares.linkToken
    var permission by ListShares.permission
    var createdAt by ListShares.createdAt
    var expiresAt by ListShares.expiresAt
}
