package no.shoppinglist.shared.api

import platform.Foundation.NSUserDefaults

actual class TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getAccessToken(): String? = defaults.stringForKey("access_token")

    actual fun getRefreshToken(): String? = defaults.stringForKey("refresh_token")

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        defaults.setObject(accessToken, forKey = "access_token")
        defaults.setObject(refreshToken, forKey = "refresh_token")
    }

    actual fun clearTokens() {
        defaults.removeObjectForKey("access_token")
        defaults.removeObjectForKey("refresh_token")
    }
}
