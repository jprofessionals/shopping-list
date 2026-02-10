package no.shoppinglist.shared.api

import android.content.Context
import android.content.SharedPreferences

actual class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)

    actual fun getAccessToken(): String? = prefs.getString("access_token", null)

    actual fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    actual fun clearTokens() {
        prefs.edit().clear().apply()
    }
}
