package no.shoppinglist.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.shoppinglist.shared.api.AuthException
import no.shoppinglist.shared.api.TokenStore
import no.shoppinglist.shared.api.dto.UserResponse
import no.shoppinglist.shared.api.routes.AuthApi
import no.shoppinglist.shared.cache.ShoppingListDatabase

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val database: ShoppingListDatabase,
) {
    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    val currentUser: StateFlow<UserResponse?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(tokenStore.getAccessToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    suspend fun login(email: String, password: String) {
        val response = authApi.login(email, password)
        tokenStore.saveTokens(response.token, response.refreshToken)
        _currentUser.value = response.user
        _isLoggedIn.value = true
    }

    suspend fun register(email: String, displayName: String, password: String) {
        val response = authApi.register(email, displayName, password)
        tokenStore.saveTokens(response.token, response.refreshToken)
        _currentUser.value = response.user
        _isLoggedIn.value = true
    }

    suspend fun logout() {
        try {
            authApi.logout()
        } catch (_: Exception) {
            // Best-effort server-side logout; always clear locally
        }
        tokenStore.clearTokens()
        clearLocalData()
        _currentUser.value = null
        _isLoggedIn.value = false
    }

    fun onSessionExpired() {
        clearLocalData()
        _currentUser.value = null
        _isLoggedIn.value = false
    }

    private fun clearLocalData() {
        database.shoppingListQueries.deleteAll()
        database.listItemQueries.deleteAll()
        database.householdQueries.deleteAllHouseholds()
        database.householdQueries.deleteAllMembers()
        database.syncQueueQueries.deleteAll()
        database.recurringItemQueries.deleteAll()
    }

    suspend fun loadCurrentUser() {
        if (tokenStore.getAccessToken() == null) {
            _isLoggedIn.value = false
            _currentUser.value = null
            return
        }
        try {
            val user = authApi.getMe()
            _currentUser.value = user
            _isLoggedIn.value = true
        } catch (_: AuthException) {
            // Token is truly invalid/expired - log out
            _currentUser.value = null
            _isLoggedIn.value = false
            tokenStore.clearTokens()
        } catch (_: Exception) {
            // Network or other transient error - keep current login state
        }
    }
}
