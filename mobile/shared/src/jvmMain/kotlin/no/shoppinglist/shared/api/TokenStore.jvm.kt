package no.shoppinglist.shared.api

actual class TokenStore {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    actual fun getAccessToken(): String? = accessToken
    actual fun getRefreshToken(): String? = refreshToken
    actual fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }
    actual fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
}
