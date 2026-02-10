package no.shoppinglist.android.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.shoppinglist.android.i18n.t
import no.shoppinglist.android.viewmodel.CommentsViewModel
import no.shoppinglist.shared.api.dto.CommentResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun CommentsSection(
    targetType: String,
    targetId: String,
    commentsViewModel: CommentsViewModel = koinViewModel(),
) {
    val uiState by commentsViewModel.uiState.collectAsStateWithLifecycle()
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var newCommentText by rememberSaveable { mutableStateOf("") }
    var editingComment by remember { mutableStateOf<CommentResponse?>(null) }
    var deletingComment by remember { mutableStateOf<CommentResponse?>(null) }

    LaunchedEffect(targetType, targetId) {
        commentsViewModel.loadComments(targetType, targetId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("comments.count", "count" to uiState.comments.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) t("comments.collapse") else t("comments.expand"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                if (uiState.comments.isEmpty()) {
                    Text(
                        text = t("comments.beTheFirst"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    uiState.comments.forEach { comment ->
                        CommentRow(
                            comment = comment,
                            onEdit = { editingComment = comment },
                            onDelete = { deletingComment = comment },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text(t("comments.placeholder")) },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotBlank()) {
                                commentsViewModel.addComment(targetType, targetId, newCommentText.trim())
                                newCommentText = ""
                            }
                        },
                        enabled = newCommentText.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = t("comments.sendComment"),
                            tint = if (newCommentText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }

    editingComment?.let { comment ->
        EditCommentDialog(
            comment = comment,
            onDismiss = { editingComment = null },
            onSave = { text ->
                commentsViewModel.updateComment(targetType, targetId, comment.id, text)
                editingComment = null
            },
        )
    }

    deletingComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text(t("comments.deleteComment")) },
            text = { Text(t("comments.confirmDelete")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        commentsViewModel.deleteComment(targetType, targetId, comment.id)
                        deletingComment = null
                    },
                ) {
                    Text(t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingComment = null }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }
}

@Composable
private fun CommentRow(
    comment: CommentResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = comment.authorName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.editedAt != null) {
                    Text(
                        text = " " + t("comments.edited"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = t("comments.edit"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("common.delete"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EditCommentDialog(
    comment: CommentResponse,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(comment.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("comments.editComment")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(t("comments.comment")) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(t("common.save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("common.cancel"))
            }
        },
    )
}
