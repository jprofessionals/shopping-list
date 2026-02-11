package no.shoppinglist.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.PreferencesResponse
import no.shoppinglist.shared.api.dto.UserResponse
import no.shoppinglist.shared.repository.AuthRepository
import no.shoppinglist.shared.repository.PreferencesRepository

data class SettingsUiState(
    val user: UserResponse? = null,
    val preferences: PreferencesResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Keep user in sync from the auth repository
        authRepository.currentUser
            .onEach { user ->
                _uiState.update { it.copy(user = user) }
            }
            .launchIn(viewModelScope)

        // Keep preferences in sync from the preferences repository
        preferencesRepository.preferences
            .onEach { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
            .launchIn(viewModelScope)

        // Fetch preferences from the server on init
        loadPreferences()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                preferencesRepository.load()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load preferences",
                    )
                }
            }
        }
    }

    fun updatePreferences(
        smartParsingEnabled: Boolean? = null,
        defaultQuantity: Double? = null,
        theme: String? = null,
        notifyNewList: Boolean? = null,
        notifyItemAdded: Boolean? = null,
        notifyNewComment: Boolean? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                preferencesRepository.update(
                    smartParsingEnabled = smartParsingEnabled,
                    defaultQuantity = defaultQuantity,
                    theme = theme,
                    notifyNewList = notifyNewList,
                    notifyItemAdded = notifyItemAdded,
                    notifyNewComment = notifyNewComment,
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update preferences")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.logout()
            _uiState.update {
                it.copy(isLoading = false, user = null, preferences = null)
            }
        }
    }
}
