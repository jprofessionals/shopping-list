package no.shoppinglist.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.shoppinglist.shared.api.dto.CommentResponse
import no.shoppinglist.shared.repository.CommentRepository

data class CommentsUiState(
    val comments: List<CommentResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class CommentsViewModel(
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    fun loadComments(targetType: String, targetId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val comments = when (targetType) {
                    "list" -> commentRepository.getListComments(targetId)
                    else -> commentRepository.getListComments(targetId)
                }
                _uiState.update { it.copy(isLoading = false, comments = comments) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load comments",
                    )
                }
            }
        }
    }

    fun addComment(targetType: String, targetId: String, text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val comment = when (targetType) {
                    "list" -> commentRepository.createListComment(targetId, text)
                    else -> commentRepository.createListComment(targetId, text)
                }
                _uiState.update { state ->
                    state.copy(comments = state.comments + comment)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to add comment")
                }
            }
        }
    }

    fun updateComment(targetType: String, targetId: String, commentId: String, text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val updatedComment = when (targetType) {
                    "list" -> commentRepository.updateListComment(targetId, commentId, text)
                    else -> commentRepository.updateListComment(targetId, commentId, text)
                }
                _uiState.update { state ->
                    state.copy(
                        comments = state.comments.map { comment ->
                            if (comment.id == commentId) updatedComment else comment
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update comment")
                }
            }
        }
    }

    fun deleteComment(targetType: String, targetId: String, commentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                when (targetType) {
                    "list" -> commentRepository.deleteListComment(targetId, commentId)
                    else -> commentRepository.deleteListComment(targetId, commentId)
                }
                _uiState.update { state ->
                    state.copy(
                        comments = state.comments.filter { it.id != commentId },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete comment")
                }
            }
        }
    }
}
