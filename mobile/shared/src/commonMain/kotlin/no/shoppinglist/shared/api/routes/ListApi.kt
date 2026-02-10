package no.shoppinglist.shared.api.routes

import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.ActivityResponse
import no.shoppinglist.shared.api.dto.BulkCreateItemsRequest
import no.shoppinglist.shared.api.dto.ClearCheckedResponse
import no.shoppinglist.shared.api.dto.CreateItemRequest
import no.shoppinglist.shared.api.dto.CreateListRequest
import no.shoppinglist.shared.api.dto.ItemResponse
import no.shoppinglist.shared.api.dto.ListDetailResponse
import no.shoppinglist.shared.api.dto.ListResponse
import no.shoppinglist.shared.api.dto.SuggestionResponse
import no.shoppinglist.shared.api.dto.UpdateItemRequest
import no.shoppinglist.shared.api.dto.UpdateListRequest

open class ListApi(private val apiClient: ApiClient? = null) {

    private fun requireClient(): ApiClient =
        apiClient ?: error("ApiClient not provided")

    open suspend fun getLists(): List<ListResponse> =
        requireClient().authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists")
        }

    open suspend fun getList(id: String): ListDetailResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists", id)
        }

    open suspend fun createList(request: CreateListRequest): ListResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun updateList(id: String, request: UpdateListRequest): ListResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("lists", id)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun deleteList(id: String) {
        requireClient().authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", id)
        }
    }

    open suspend fun addItem(listId: String, request: CreateItemRequest): ItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun bulkAddItems(
        listId: String,
        request: BulkCreateItemsRequest,
    ): List<ItemResponse> =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items", "bulk")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun updateItem(
        listId: String,
        itemId: String,
        request: UpdateItemRequest,
    ): ItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("lists", listId, "items", itemId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun deleteItem(listId: String, itemId: String) {
        requireClient().authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "items", itemId)
        }
    }

    open suspend fun toggleCheck(listId: String, itemId: String): ItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items", itemId, "check")
        }

    open suspend fun clearChecked(listId: String): ClearCheckedResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "items", "checked")
        }

    open suspend fun pinList(id: String) {
        requireClient().authenticatedRequest<Unit> {
            method = HttpMethod.Post
            url.appendPathSegments("lists", id, "pin")
        }
    }

    open suspend fun unpinList(id: String) {
        requireClient().authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", id, "pin")
        }
    }

    open suspend fun getActivity(listId: String, limit: Int = 20): List<ActivityResponse> =
        requireClient().authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists", listId, "activity")
            parameter("limit", limit)
        }

    open suspend fun getSuggestions(query: String, limit: Int = 10): List<SuggestionResponse> =
        requireClient().authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("lists", "suggestions")
            parameter("query", query)
            parameter("limit", limit)
        }
}
