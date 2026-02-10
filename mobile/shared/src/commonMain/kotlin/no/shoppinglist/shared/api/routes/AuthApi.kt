package no.shoppinglist.shared.api.routes

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.AuthConfigResponse
import no.shoppinglist.shared.api.dto.LocalLoginRequest
import no.shoppinglist.shared.api.dto.LocalRegisterRequest
import no.shoppinglist.shared.api.dto.LoginResponse
import no.shoppinglist.shared.api.dto.RefreshRequest
import no.shoppinglist.shared.api.dto.RefreshResponse
import no.shoppinglist.shared.api.dto.UserResponse

class AuthApi(private val apiClient: ApiClient) {

    suspend fun getConfig(): AuthConfigResponse =
        apiClient.unauthenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("auth", "config")
        }

    suspend fun login(email: String, password: String): LoginResponse =
        apiClient.unauthenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("auth", "login")
            contentType(ContentType.Application.Json)
            setBody(LocalLoginRequest(email = email, password = password))
        }

    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): LoginResponse =
        apiClient.unauthenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("auth", "register")
            contentType(ContentType.Application.Json)
            setBody(
                LocalRegisterRequest(
                    email = email,
                    displayName = displayName,
                    password = password,
                )
            )
        }

    suspend fun refreshToken(refreshToken: String): RefreshResponse =
        apiClient.unauthenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("auth", "refresh")
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = refreshToken))
        }

    suspend fun getMe(): UserResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("auth", "me")
        }

    suspend fun logout() {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Post
            url.appendPathSegments("auth", "logout")
        }
    }
}
