package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object RefreshTokens : UUIDTable("refresh_tokens") {
    val accountId = reference("account_id", Accounts)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
}

class RefreshToken(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RefreshToken>(RefreshTokens)

    var accountId by RefreshTokens.accountId
    var tokenHash by RefreshTokens.tokenHash
    var expiresAt by RefreshTokens.expiresAt
    var createdAt by RefreshTokens.createdAt
}
