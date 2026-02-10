package no.shoppinglist.shared.api.routes

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.CreateShareRequest
import no.shoppinglist.shared.api.dto.ShareResponse
import no.shoppinglist.shared.api.dto.SharedListResponse

class ShareApi(private val apiClient: ApiClient) {

    suspend fun getShares(listId: String): List<ShareResponse> =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists", listId, "shares")
        }

    suspend fun createShare(
        listId: String,
        request: CreateShareRequest,
    ): ShareResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "shares")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteShare(listId: String, shareId: String) {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "shares", shareId)
        }
    }

    suspend fun getSharedList(token: String): SharedListResponse =
        apiClient.unauthenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("shared", token)
        }
}
