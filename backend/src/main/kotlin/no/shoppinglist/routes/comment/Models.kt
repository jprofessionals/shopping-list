package no.shoppinglist.routes.comment

import kotlinx.serialization.Serializable
import no.shoppinglist.domain.Comment

@Serializable
data class CreateCommentRequest(
    val text: String,
)

@Serializable
data class UpdateCommentRequest(
    val text: String,
)

@Serializable
data class CommentResponse(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val editedAt: String?,
    val createdAt: String,
)

internal fun Comment.toResponse() =
    CommentResponse(
        id = id.value.toString(),
        text = text,
        authorId = author.id.value.toString(),
        authorName = author.displayName,
        authorAvatarUrl = author.avatarUrl,
        editedAt = editedAt?.toString(),
        createdAt = createdAt.toString(),
    )
