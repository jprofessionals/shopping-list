package no.shoppinglist.shared.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.shoppinglist.shared.api.dto.PreferencesResponse
import no.shoppinglist.shared.api.dto.UpdatePreferencesRequest
import no.shoppinglist.shared.api.routes.PreferencesApi

class PreferencesRepository(
    private val preferencesApi: PreferencesApi,
) {
    private val _preferences = MutableStateFlow<PreferencesResponse?>(null)
    val preferences: StateFlow<PreferencesResponse?> = _preferences.asStateFlow()

    suspend fun load() {
        try {
            val response = preferencesApi.getPreferences()
            _preferences.value = response
        } catch (_: Exception) {
            // If we can't load, keep whatever we have cached in memory
        }
    }

    suspend fun update(
        smartParsingEnabled: Boolean? = null,
        defaultQuantity: Double? = null,
        theme: String? = null,
        notifyNewList: Boolean? = null,
        notifyItemAdded: Boolean? = null,
        notifyNewComment: Boolean? = null,
    ) {
        val request = UpdatePreferencesRequest(
            smartParsingEnabled = smartParsingEnabled,
            defaultQuantity = defaultQuantity,
            theme = theme,
            notifyNewList = notifyNewList,
            notifyItemAdded = notifyItemAdded,
            notifyNewComment = notifyNewComment,
        )
        val response = preferencesApi.updatePreferences(request)
        _preferences.value = response
    }
}
