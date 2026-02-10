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
import no.shoppinglist.shared.cache.HouseholdEntity
import no.shoppinglist.shared.repository.HouseholdRepository

data class HouseholdsUiState(
    val households: List<HouseholdEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HouseholdsViewModel(
    private val householdRepository: HouseholdRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdsUiState())
    val uiState: StateFlow<HouseholdsUiState> = _uiState.asStateFlow()

    init {
        // Collect cached households from the repository
        householdRepository.households
            .onEach { households ->
                _uiState.update { it.copy(households = households) }
            }
            .launchIn(viewModelScope)

        // Initial server fetch
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                householdRepository.getAll()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to refresh households",
                    )
                }
            }
        }
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.create(name)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to create household")
                }
            }
        }
    }

    fun deleteHousehold(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                householdRepository.delete(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete household")
                }
            }
        }
    }
}
