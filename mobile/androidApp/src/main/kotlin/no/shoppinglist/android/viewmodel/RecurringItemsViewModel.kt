package no.shoppinglist.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.cache.RecurringItemEntity
import no.shoppinglist.shared.repository.RecurringItemRepository

data class RecurringItemsUiState(
    val items: List<RecurringItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RecurringItemsViewModel(
    private val recurringItemRepository: RecurringItemRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val householdId: String = checkNotNull(savedStateHandle["householdId"])

    private val _uiState = MutableStateFlow(RecurringItemsUiState())
    val uiState: StateFlow<RecurringItemsUiState> = _uiState.asStateFlow()

    init {
        // Observe local DB changes
        viewModelScope.launch {
            recurringItemRepository.getByHousehold(householdId).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                recurringItemRepository.fetchAll(householdId)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load recurring items")
                }
            }
        }
    }

    fun create(name: String, quantity: Double, unit: String?, frequency: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                recurringItemRepository.create(householdId, name, quantity, unit, frequency)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create item") }
            }
        }
    }

    fun update(itemId: String, name: String, quantity: Double, unit: String?, frequency: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                recurringItemRepository.update(householdId, itemId, name, quantity, unit, frequency)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update item") }
            }
        }
    }

    fun delete(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                recurringItemRepository.delete(householdId, itemId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete item") }
            }
        }
    }

    fun pause(itemId: String, until: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                recurringItemRepository.pause(householdId, itemId, until)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to pause item") }
            }
        }
    }

    fun resume(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                recurringItemRepository.resume(householdId, itemId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to resume item") }
            }
        }
    }
}
