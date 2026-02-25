package no.shoppinglist.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.shoppinglist.shared.api.dto.RefreshRequest
import no.shoppinglist.shared.api.dto.RefreshResponse

class ApiClient(
    @PublishedApi internal val baseUrl: String,
    @PublishedApi internal val tokenStore: TokenStore,
) {
    var onSessionExpired: (() -> Unit)? = null

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @PublishedApi
    internal val httpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }

    suspend inline fun <reified T> authenticatedRequest(
        block: HttpRequestBuilder.() -> Unit,
    ): T {
        val token = tokenStore.getAccessToken() ?: throw AuthException("Not logged in")

        val response: HttpResponse = httpClient.request {
            url.takeFrom(baseUrl)
            bearerAuth(token)
            block()
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshToken = tokenStore.getRefreshToken()
                ?: throw AuthException("No refresh token")

            val refreshResponse: HttpResponse = httpClient.request {
                url.takeFrom(baseUrl)
                method = io.ktor.http.HttpMethod.Post
                url.appendPathSegments("auth", "refresh")
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }

            if (refreshResponse.status == HttpStatusCode.OK) {
                val tokens = refreshResponse.body<RefreshResponse>()
                tokenStore.saveTokens(tokens.token, tokens.refreshToken)

                val retryResponse: HttpResponse = httpClient.request {
                    url.takeFrom(baseUrl)
                    bearerAuth(tokens.token)
                    block()
                }
                return retryResponse.body()
            }

            tokenStore.clearTokens()
            onSessionExpired?.invoke()
            throw AuthException("Session expired")
        }

        return response.body()
    }

    suspend inline fun <reified T> unauthenticatedRequest(
        block: HttpRequestBuilder.() -> Unit,
    ): T {
        val response: HttpResponse = httpClient.request {
            url.takeFrom(baseUrl)
            block()
        }
        return response.body()
    }
}

class AuthException(message: String) : Exception(message)
