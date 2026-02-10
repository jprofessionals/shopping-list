package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object Accounts : UUIDTable("accounts") {
    val email = varchar("email", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val googleId = varchar("google_id", 255).uniqueIndex().nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val createdAt = timestamp("created_at")
}

class Account(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(Accounts)

    var email by Accounts.email
    var displayName by Accounts.displayName
    var avatarUrl by Accounts.avatarUrl
    var googleId by Accounts.googleId
    var passwordHash by Accounts.passwordHash
    var createdAt by Accounts.createdAt
}
