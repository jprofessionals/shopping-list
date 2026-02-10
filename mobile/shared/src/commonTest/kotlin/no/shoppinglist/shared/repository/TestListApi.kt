package no.shoppinglist.shared.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
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
import no.shoppinglist.shared.api.routes.ListApi

/**
 * Test-specific subclass of ListApi that uses a pre-configured HttpClient
 * (typically with MockEngine) instead of going through ApiClient + TokenStore.
 * This avoids the need to instantiate expect/actual classes in common test code.
 *
 * All methods are overridden to use the injected httpClient directly.
 */
class TestListApi(
    private val httpClient: HttpClient,
    private val json: Json,
) : ListApi() {

    override suspend fun getLists(): List<ListResponse> =
        httpClient.request {
            method = HttpMethod.Get
            url.appendPathSegments("lists")
        }.body()

    override suspend fun getList(id: String): ListDetailResponse =
        httpClient.request {
            method = HttpMethod.Get
            url.appendPathSegments("lists", id)
        }.body()

    override suspend fun createList(request: CreateListRequest): ListResponse =
        httpClient.request {
            method = HttpMethod.Post
            url.appendPathSegments("lists")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun updateList(id: String, request: UpdateListRequest): ListResponse =
        httpClient.request {
            method = HttpMethod.Patch
            url.appendPathSegments("lists", id)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun deleteList(id: String) {
        httpClient.request {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", id)
        }
    }

    override suspend fun addItem(listId: String, request: CreateItemRequest): ItemResponse =
        httpClient.request {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun bulkAddItems(
        listId: String,
        request: BulkCreateItemsRequest,
    ): List<ItemResponse> =
        httpClient.request {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items", "bulk")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun updateItem(
        listId: String,
        itemId: String,
        request: UpdateItemRequest,
    ): ItemResponse =
        httpClient.request {
            method = HttpMethod.Patch
            url.appendPathSegments("lists", listId, "items", itemId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun deleteItem(listId: String, itemId: String) {
        httpClient.request {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "items", itemId)
        }
    }

    override suspend fun toggleCheck(listId: String, itemId: String): ItemResponse =
        httpClient.request {
            method = HttpMethod.Post
            url.appendPathSegments("lists", listId, "items", itemId, "toggle")
        }.body()

    override suspend fun clearChecked(listId: String): ClearCheckedResponse =
        httpClient.request {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", listId, "items", "checked")
        }.body()

    override suspend fun pinList(id: String) {
        httpClient.request {
            method = HttpMethod.Post
            url.appendPathSegments("lists", id, "pin")
        }
    }

    override suspend fun unpinList(id: String) {
        httpClient.request {
            method = HttpMethod.Delete
            url.appendPathSegments("lists", id, "pin")
        }
    }

    override suspend fun getActivity(listId: String, limit: Int): List<ActivityResponse> =
        httpClient.request {
            method = HttpMethod.Get
            url.appendPathSegments("lists", listId, "activity")
            parameter("limit", limit)
        }.body()

    override suspend fun getSuggestions(query: String, limit: Int): List<SuggestionResponse> =
        httpClient.request {
            method = HttpMethod.Get
            url.appendPathSegments("lists", "suggestions")
            parameter("query", query)
            parameter("limit", limit)
        }.body()
}
