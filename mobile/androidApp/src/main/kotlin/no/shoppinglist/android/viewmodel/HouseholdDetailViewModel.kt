package no.shoppinglist.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.MemberResponse
import no.shoppinglist.shared.repository.AuthRepository
import no.shoppinglist.shared.repository.HouseholdRepository

data class HouseholdDetailUiState(
    val householdId: String = "",
    val name: String = "",
    val members: List<MemberResponse> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isOwner: Boolean
        get() = currentUserId != null && members.any { it.accountId == currentUserId && it.role == "OWNER" }
}

class HouseholdDetailViewModel(
    private val householdRepository: HouseholdRepository,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val householdId: String = checkNotNull(savedStateHandle["householdId"])

    private val _uiState = MutableStateFlow(HouseholdDetailUiState(householdId = householdId))
    val uiState: StateFlow<HouseholdDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currentUser = authRepository.currentUser.value
                val detail = householdRepository.getDetail(householdId)
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            members = detail.members,
                            currentUserId = currentUser?.id,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, currentUserId = currentUser?.id) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load household detail",
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.update(householdId, name)
                _uiState.update { it.copy(name = name) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update household name")
                }
            }
        }
    }

    fun addMember(email: String, role: String = "MEMBER") {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.addMember(householdId, email, role)
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to add member")
                }
            }
        }
    }

    fun updateMemberRole(accountId: String, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.updateMemberRole(householdId, accountId, role)
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update member role")
                }
            }
        }
    }

    fun removeMember(accountId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.removeMember(householdId, accountId)
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to remove member")
                }
            }
        }
    }
}
