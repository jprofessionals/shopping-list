package no.shoppinglist.shared.api.routes

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.CreateRecurringItemRequest
import no.shoppinglist.shared.api.dto.PauseRecurringItemRequest
import no.shoppinglist.shared.api.dto.RecurringItemResponse
import no.shoppinglist.shared.api.dto.UpdateRecurringItemRequest

open class RecurringItemApi(private val apiClient: ApiClient? = null) {

    private fun requireClient(): ApiClient =
        apiClient ?: error("ApiClient not provided")

    open suspend fun getItems(householdId: String): List<RecurringItemResponse> =
        requireClient().authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("households", householdId, "recurring-items")
        }

    open suspend fun createItem(
        householdId: String,
        request: CreateRecurringItemRequest,
    ): RecurringItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households", householdId, "recurring-items")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun updateItem(
        householdId: String,
        itemId: String,
        request: UpdateRecurringItemRequest,
    ): RecurringItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("households", householdId, "recurring-items", itemId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun deleteItem(householdId: String, itemId: String) {
        requireClient().authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("households", householdId, "recurring-items", itemId)
        }
    }

    open suspend fun pauseItem(
        householdId: String,
        itemId: String,
        request: PauseRecurringItemRequest = PauseRecurringItemRequest(),
    ): RecurringItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households", householdId, "recurring-items", itemId, "pause")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    open suspend fun resumeItem(
        householdId: String,
        itemId: String,
    ): RecurringItemResponse =
        requireClient().authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households", householdId, "recurring-items", itemId, "resume")
        }
}
