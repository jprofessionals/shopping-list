package no.shoppinglist.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.shoppinglist.config.GoogleAuthConfig
import java.net.URLEncoder

@Serializable
data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String,
    val picture: String? = null,
)

class GoogleAuthService(
    private val config: GoogleAuthConfig,
) {
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }

    fun getAuthorizationUrl(state: String): String {
        val params =
            mapOf(
                "client_id" to config.clientId,
                "redirect_uri" to config.callbackUrl,
                "response_type" to "code",
                "scope" to "openid email profile",
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent",
            )
        val queryString =
            params.entries.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, "UTF-8")}"
            }
        return "https://accounts.google.com/o/oauth2/v2/auth?$queryString"
    }

    suspend fun exchangeCodeForTokens(code: String): GoogleTokenResponse =
        client
            .post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "code=$code" +
                        "&client_id=${config.clientId}" +
                        "&client_secret=${config.clientSecret}" +
                        "&redirect_uri=${URLEncoder.encode(config.callbackUrl, "UTF-8")}" +
                        "&grant_type=authorization_code",
                )
            }.body()

    suspend fun getUserInfo(accessToken: String): GoogleUserInfo =
        client
            .get("https://www.googleapis.com/oauth2/v2/userinfo") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }.body()
}
