package no.shoppinglist.shared.api.routes

import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.CommentResponse
import no.shoppinglist.shared.api.dto.CreateCommentRequest
import no.shoppinglist.shared.api.dto.UpdateCommentRequest

class CommentApi(private val apiClient: ApiClient) {

    // List comments

    suspend fun getListComments(
        listId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<CommentResponse> =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists", listId, "comments")
            parameter("limit", limit)
            parameter("offset", offset)
        }

    suspend fun createListComment(
        listId: String,
        request: CreateCommentRequest,
    ): CommentResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "comments")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateListComment(
        listId: String,
        commentId: String,
        request: UpdateCommentRequest,
    ): CommentResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("lists", listId, "comments", commentId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteListComment(listId: String, commentId: String) {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "comments", commentId)
        }
    }

    // Household comments

    suspend fun getHouseholdComments(
        householdId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<CommentResponse> =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("households", householdId, "comments")
            parameter("limit", limit)
            parameter("offset", offset)
        }

    suspend fun createHouseholdComment(
        householdId: String,
        request: CreateCommentRequest,
    ): CommentResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households", householdId, "comments")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateHouseholdComment(
        householdId: String,
        commentId: String,
        request: UpdateCommentRequest,
    ): CommentResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("households", householdId, "comments", commentId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteHouseholdComment(householdId: String, commentId: String) {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("households", householdId, "comments", commentId)
        }
    }
}
