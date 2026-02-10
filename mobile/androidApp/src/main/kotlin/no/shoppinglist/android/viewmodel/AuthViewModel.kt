package no.shoppinglist.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.UserResponse
import no.shoppinglist.shared.repository.AuthRepository

data class AuthUiState(
    val isLoading: Boolean = false,
    val user: UserResponse? = null,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        combine(
            authRepository.currentUser,
            authRepository.isLoggedIn,
        ) { user, loggedIn ->
            _uiState.update { state ->
                state.copy(user = user, isLoggedIn = loggedIn)
            }
        }.launchIn(viewModelScope)

        // Validate stored token and load user profile on startup
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.loadCurrentUser()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.login(email, password)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed",
                    )
                }
            }
        }
    }

    fun register(email: String, displayName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.register(email, displayName, password)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed",
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.logout()
            _uiState.update {
                it.copy(isLoading = false, user = null, isLoggedIn = false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
