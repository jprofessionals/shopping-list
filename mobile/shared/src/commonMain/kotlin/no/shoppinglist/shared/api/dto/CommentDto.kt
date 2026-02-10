package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

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

@Serializable
data class CreateCommentRequest(
    val text: String,
)

@Serializable
data class UpdateCommentRequest(
    val text: String,
)
