package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

enum class CommentTargetType {
    LIST,
    HOUSEHOLD,
}

object Comments : UUIDTable("comments") {
    val targetType = enumerationByName<CommentTargetType>("target_type", 20)
    val targetId = uuid("target_id").index()
    val author = reference("author_id", Accounts)
    val text = text("text")
    val editedAt = timestamp("edited_at").nullable()
    val createdAt = timestamp("created_at")

    init {
        index(isUnique = false, targetType, targetId, createdAt)
    }
}

class Comment(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Comment>(Comments)

    var targetType by Comments.targetType
    var targetId by Comments.targetId
    var author by Account referencedOn Comments.author
    var text by Comments.text
    var editedAt by Comments.editedAt
    var createdAt by Comments.createdAt
}
