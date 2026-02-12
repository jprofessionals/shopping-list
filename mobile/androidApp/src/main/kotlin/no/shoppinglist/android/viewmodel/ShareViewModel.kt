package no.shoppinglist.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.ShareResponse
import no.shoppinglist.shared.repository.ShareRepository

data class ShareUiState(
    val shares: List<ShareResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val newShareLink: String? = null,
)

class ShareViewModel(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun loadShares(listId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val shares = shareRepository.getShares(listId)
                _uiState.update { it.copy(isLoading = false, shares = shares) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load shares",
                    )
                }
            }
        }
    }

    fun createUserShare(listId: String, permission: String, accountId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val share = shareRepository.createShare(
                    listId = listId,
                    type = "user",
                    permission = permission,
                    accountId = accountId,
                    expirationHours = 0,
                )
                _uiState.update {
                    it.copy(shares = it.shares + share)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to create user share")
                }
            }
        }
    }

    fun createLinkShare(listId: String, permission: String, expirationHours: Int = 24) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val share = shareRepository.createShare(
                    listId = listId,
                    type = "link",
                    permission = permission,
                    accountId = null,
                    expirationHours = expirationHours,
                )
                _uiState.update {
                    it.copy(
                        shares = it.shares + share,
                        newShareLink = share.linkToken?.let { token ->
                            "shoppinglist://shared/$token"
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to create link share")
                }
            }
        }
    }

    fun deleteShare(listId: String, shareId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                shareRepository.deleteShare(listId, shareId)
                _uiState.update { state ->
                    state.copy(shares = state.shares.filter { it.id != shareId })
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete share")
                }
            }
        }
    }

    fun clearNewShareLink() {
        _uiState.update { it.copy(newShareLink = null) }
    }
}
