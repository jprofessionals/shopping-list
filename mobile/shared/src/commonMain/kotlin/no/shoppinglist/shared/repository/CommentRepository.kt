package no.shoppinglist.shared.repository

import no.shoppinglist.shared.api.dto.CommentResponse
import no.shoppinglist.shared.api.dto.CreateCommentRequest
import no.shoppinglist.shared.api.dto.UpdateCommentRequest
import no.shoppinglist.shared.api.routes.CommentApi

class CommentRepository(
    private val commentApi: CommentApi,
) {
    // List comments

    suspend fun getListComments(
        listId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<CommentResponse> =
        commentApi.getListComments(listId, limit, offset)

    suspend fun createListComment(listId: String, text: String): CommentResponse =
        commentApi.createListComment(listId, CreateCommentRequest(text = text))

    suspend fun updateListComment(
        listId: String,
        commentId: String,
        text: String,
    ): CommentResponse =
        commentApi.updateListComment(listId, commentId, UpdateCommentRequest(text = text))

    suspend fun deleteListComment(listId: String, commentId: String) =
        commentApi.deleteListComment(listId, commentId)

    // Household comments

    suspend fun getHouseholdComments(
        householdId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<CommentResponse> =
        commentApi.getHouseholdComments(householdId, limit, offset)

    suspend fun createHouseholdComment(
        householdId: String,
        text: String,
    ): CommentResponse =
        commentApi.createHouseholdComment(householdId, CreateCommentRequest(text = text))

    suspend fun updateHouseholdComment(
        householdId: String,
        commentId: String,
        text: String,
    ): CommentResponse =
        commentApi.updateHouseholdComment(
            householdId,
            commentId,
            UpdateCommentRequest(text = text),
        )

    suspend fun deleteHouseholdComment(householdId: String, commentId: String) =
        commentApi.deleteHouseholdComment(householdId, commentId)
}
