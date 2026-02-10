package no.shoppinglist.shared.api.routes

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.PreferencesResponse
import no.shoppinglist.shared.api.dto.UpdatePreferencesRequest

class PreferencesApi(private val apiClient: ApiClient) {

    suspend fun getPreferences(): PreferencesResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("preferences")
        }

    suspend fun updatePreferences(request: UpdatePreferencesRequest): PreferencesResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("preferences")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
}
