package no.shoppinglist.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.cache.ShoppingListEntity
import no.shoppinglist.shared.repository.ListRepository
import no.shoppinglist.shared.sync.ConnectivityMonitor
import no.shoppinglist.shared.sync.SyncManager

data class ListsUiState(
    val lists: List<ShoppingListEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ListsViewModel(
    private val listRepository: ListRepository,
    private val syncManager: SyncManager,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    val isConnected: StateFlow<Boolean> = connectivityMonitor.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        // Collect cached lists from the repository
        listRepository.lists
            .onEach { lists ->
                _uiState.update { it.copy(lists = lists) }
            }
            .launchIn(viewModelScope)

        // Initial fetch from the server
        refresh()

        // Re-sync when connectivity is restored
        connectivityMonitor.isConnected
            .onEach { connected ->
                if (connected) {
                    viewModelScope.launch {
                        syncManager.syncPendingChanges()
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                listRepository.getAllLists()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to refresh lists",
                    )
                }
            }
        }
    }

    fun createList(name: String, householdId: String?, isPersonal: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.createList(name, householdId, isPersonal)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to create list")
                }
            }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                listRepository.deleteList(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete list")
                }
            }
        }
    }

    fun togglePin(id: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                if (currentlyPinned) {
                    listRepository.unpinList(id)
                } else {
                    listRepository.pinList(id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update pin status")
                }
            }
        }
    }
}
