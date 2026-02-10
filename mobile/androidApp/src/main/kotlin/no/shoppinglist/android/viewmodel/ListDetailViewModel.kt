package no.shoppinglist.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.ActivityResponse
import no.shoppinglist.shared.api.dto.SuggestionResponse
import no.shoppinglist.shared.cache.ListItemEntity
import no.shoppinglist.shared.repository.ListRepository

data class ListDetailUiState(
    val listId: String = "",
    val listName: String = "",
    val items: List<ListItemEntity> = emptyList(),
    val isOwner: Boolean = false,
    val isPinned: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val suggestions: List<SuggestionResponse> = emptyList(),
    val activity: List<ActivityResponse> = emptyList(),
)

class ListDetailViewModel(
    private val listRepository: ListRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = checkNotNull(savedStateHandle["listId"])

    private val _uiState = MutableStateFlow(ListDetailUiState(listId = listId))
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    init {
        // Collect cached items for this list
        listRepository.getListItems(listId)
            .onEach { items ->
                _uiState.update { it.copy(items = items) }
            }
            .launchIn(viewModelScope)

        // Load full detail from the server
        loadListDetail()
    }

    private fun loadListDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = listRepository.getListDetail(listId)
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            listName = detail.name,
                            isOwner = detail.isOwner,
                            isPinned = detail.isPinned,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load list detail",
                    )
                }
            }
        }
    }

    fun addItem(name: String, quantity: Double = 1.0, unit: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.addItem(listId, name, quantity, unit)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to add item")
                }
            }
        }
    }

    fun updateItem(itemId: String, name: String, quantity: Double, unit: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.updateItem(listId, itemId, name, quantity, unit)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update item")
                }
            }
        }
    }

    fun changeQuantity(item: ListItemEntity, delta: Int) {
        val newQty = (item.quantity + delta).coerceAtLeast(1.0)
        if (newQty != item.quantity) {
            updateItem(item.id, item.name, newQty, item.unit)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.deleteItem(listId, itemId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete item")
                }
            }
        }
    }

    fun toggleCheck(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.toggleCheck(listId, itemId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to toggle item")
                }
            }
        }
    }

    fun clearChecked() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.clearChecked(listId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to clear checked items")
                }
            }
        }
    }

    fun loadSuggestions(query: String) {
        viewModelScope.launch {
            try {
                val suggestions = listRepository.getSuggestions(query)
                _uiState.update { it.copy(suggestions = suggestions) }
            } catch (_: Exception) {
                _uiState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }

    fun loadActivity() {
        viewModelScope.launch {
            try {
                val activity = listRepository.getActivity(listId)
                _uiState.update { it.copy(activity = activity) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to load activity")
                }
            }
        }
    }
}
