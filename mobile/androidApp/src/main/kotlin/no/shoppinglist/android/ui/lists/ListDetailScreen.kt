package no.shoppinglist.android.ui.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import no.shoppinglist.android.ui.comments.CommentsSection
import no.shoppinglist.android.ui.common.ConnectionStatusBanner
import no.shoppinglist.android.ui.common.LoadingScreen
import no.shoppinglist.android.ui.share.ShareSheet
import no.shoppinglist.android.viewmodel.ListDetailViewModel
import no.shoppinglist.shared.cache.ListItemEntity
import no.shoppinglist.android.viewmodel.ListsViewModel
import no.shoppinglist.shared.api.dto.ActivityResponse
import no.shoppinglist.shared.api.dto.SuggestionResponse
import no.shoppinglist.android.i18n.t
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: String,
    onNavigateBack: () -> Unit,
    listDetailViewModel: ListDetailViewModel = koinViewModel(),
    listsViewModel: ListsViewModel = koinViewModel(),
) {
    val uiState by listDetailViewModel.uiState.collectAsStateWithLifecycle()
    val isConnected by listsViewModel.isConnected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var showActivitySection by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf<ListItemEntity?>(null) }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    LaunchedEffect(inputText) {
        if (inputText.length >= 2) {
            listDetailViewModel.loadSuggestions(inputText)
            showSuggestions = true
        } else {
            showSuggestions = false
        }
    }

    if (uiState.isLoading && uiState.items.isEmpty()) {
        LoadingScreen()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.listName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("common.back"))
                    }
                },
                actions = {
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = t("common.share"))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = t("common.moreOptions"))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("shoppingListView.activity")) },
                                onClick = {
                                    showOverflowMenu = false
                                    listDetailViewModel.loadActivity()
                                    showActivitySection = !showActivitySection
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(t("shoppingListView.clearChecked")) },
                                onClick = {
                                    showOverflowMenu = false
                                    listDetailViewModel.clearChecked()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ClearAll, contentDescription = null)
                                },
                            )
                            if (uiState.isOwner) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            t("shoppingListView.deleteList"),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AddItemBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                suggestions = if (showSuggestions) uiState.suggestions else emptyList(),
                onSuggestionSelected = { suggestion ->
                    listDetailViewModel.addItem(
                        suggestion.name,
                        suggestion.typicalQuantity,
                        suggestion.typicalUnit,
                    )
                    inputText = ""
                    showSuggestions = false
                },
                onAddItem = {
                    if (inputText.isNotBlank()) {
                        listDetailViewModel.addItem(inputText.trim())
                        inputText = ""
                        showSuggestions = false
                    }
                },
                onDismissSuggestions = { showSuggestions = false },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ConnectionStatusBanner(isConnected = isConnected)

            val uncheckedItems = uiState.items.filter { !it.isChecked }
            val checkedItems = uiState.items.filter { it.isChecked }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = uncheckedItems,
                    key = { it.id },
                ) { item ->
                    SwipeableItemRow(
                        item = item,
                        onToggleCheck = { listDetailViewModel.toggleCheck(item.id) },
                        onDelete = { listDetailViewModel.deleteItem(item.id) },
                        onQuantityChange = { delta -> listDetailViewModel.changeQuantity(item, delta) },
                        onClick = { showEditItemDialog = item },
                    )
                }

                if (checkedItems.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        Text(
                            text = t("shoppingListView.checkedCount", "count" to checkedItems.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(
                        items = checkedItems,
                        key = { it.id },
                    ) { item ->
                        SwipeableItemRow(
                            item = item,
                            onToggleCheck = { listDetailViewModel.toggleCheck(item.id) },
                            onDelete = { listDetailViewModel.deleteItem(item.id) },
                            onQuantityChange = { delta -> listDetailViewModel.changeQuantity(item, delta) },
                            onClick = { showEditItemDialog = item },
                        )
                    }
                }

                if (showActivitySection && uiState.activity.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActivitySection(activity = uiState.activity)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    CommentsSection(
                        targetType = "list",
                        targetId = listId,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showShareSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = sheetState,
        ) {
            ShareSheet(
                listId = listId,
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        showShareSheet = false
                    }
                },
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(t("shoppingListView.deleteList")) },
            text = { Text(t("shoppingListView.confirmDeleteList")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        listsViewModel.deleteList(listId)
                        onNavigateBack()
                    },
                ) {
                    Text(t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }

    showEditItemDialog?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { showEditItemDialog = null },
            onSave = { name, quantity, unit ->
                listDetailViewModel.updateItem(item.id, name, quantity, unit)
                showEditItemDialog = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItemRow(
    item: ListItemEntity,
    onToggleCheck: () -> Unit,
    onDelete: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "item-swipe-color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("common.delete"),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggleCheck() },
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    color = if (item.isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.unit != null || (item.isChecked && item.checkedByName != null)) {
                    val detailParts = mutableListOf<String>()
                    if (item.unit != null) {
                        detailParts.add(item.unit!!)
                    }
                    if (item.isChecked && item.checkedByName != null) {
                        detailParts.add(t("shoppingListView.byPerson", "name" to item.checkedByName!!))
                    }
                    Text(
                        text = detailParts.joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onQuantityChange(-1) },
                    modifier = Modifier.size(32.dp),
                    enabled = item.quantity > 1.0,
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = t("listItem.decreaseQuantity"),
                        modifier = Modifier.size(18.dp),
                    )
                }

                val qtyText = if (item.quantity == item.quantity.toLong().toDouble()) {
                    item.quantity.toLong().toString()
                } else {
                    item.quantity.toString()
                }
                Text(
                    text = qtyText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                IconButton(
                    onClick = { onQuantityChange(1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = t("listItem.increaseQuantity"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("shoppingListView.deleteItem"),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddItemBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    suggestions: List<SuggestionResponse>,
    onSuggestionSelected: (SuggestionResponse) -> Unit,
    onAddItem: () -> Unit,
    onDismissSuggestions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                suggestions.take(5).forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelected(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = suggestion.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val detail = buildString {
                            val qty = if (suggestion.typicalQuantity == suggestion.typicalQuantity.toLong().toDouble()) {
                                suggestion.typicalQuantity.toLong().toString()
                            } else {
                                suggestion.typicalQuantity.toString()
                            }
                            append(qty)
                            suggestion.typicalUnit?.let { append(" $it") }
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(t("shoppingListView.addItemPlaceholder")) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAddItem,
                enabled = inputText.isNotBlank(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = t("shoppingListView.addItem"),
                    tint = if (inputText.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivitySection(activity: List<ActivityResponse>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = t("shoppingListView.recentActivity"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        activity.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = buildString {
                            append(entry.actorName)
                            append(" ")
                            append(entry.type.lowercase().replace("_", " "))
                            entry.targetName?.let { append(" \"$it\"") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditItemDialog(
    item: ListItemEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Double, unit: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var quantityText by rememberSaveable {
        mutableStateOf(
            if (item.quantity == item.quantity.toLong().toDouble()) {
                item.quantity.toLong().toString()
            } else {
                item.quantity.toString()
            },
        )
    }
    var unit by rememberSaveable { mutableStateOf(item.unit ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("shoppingListView.editItem")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("common.name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(t("shoppingListView.qty")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(t("shoppingListView.unit")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                    onSave(name.trim(), qty, unit.trim().ifEmpty { null })
                },
                enabled = name.isNotBlank(),
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
