package no.shoppinglist.android.ui.lists

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.shoppinglist.android.i18n.t
import no.shoppinglist.android.ui.common.ConnectionStatusBanner
import no.shoppinglist.android.viewmodel.HouseholdsViewModel
import no.shoppinglist.android.viewmodel.ListsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListsScreen(
    onNavigateToDetail: (String) -> Unit,
    listsViewModel: ListsViewModel = koinViewModel(),
    householdsViewModel: HouseholdsViewModel = koinViewModel(),
) {
    val uiState by listsViewModel.uiState.collectAsStateWithLifecycle()
    val isConnected by listsViewModel.isConnected.collectAsStateWithLifecycle()
    val householdsState by householdsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(t("shoppingLists.title")) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    householdsViewModel.refresh()
                    showCreateDialog = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = t("shoppingLists.createList"))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ConnectionStatusBanner(isConnected = isConnected)

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { listsViewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.lists.isEmpty() && !uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = t("shoppingLists.noListsYet"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = t("shoppingLists.tapToCreate"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val pinnedLists = uiState.lists.filter { it.isPinned }
                    val unpinnedLists = uiState.lists.filter { !it.isPinned }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pinnedLists.isNotEmpty()) {
                            item {
                                Text(
                                    text = t("common.pinned"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 8.dp,
                                        bottom = 4.dp,
                                    ),
                                )
                            }
                            items(
                                items = pinnedLists,
                                key = { it.id },
                            ) { list ->
                                SwipeableListCard(
                                    listName = list.name,
                                    itemCount = list.itemCount,
                                    uncheckedCount = list.uncheckedCount,
                                    isPinned = true,
                                    isPersonal = list.isPersonal,
                                    onClick = { onNavigateToDetail(list.id) },
                                    onLongClick = {
                                        listsViewModel.togglePin(list.id, list.isPinned)
                                    },
                                    onDismiss = { showDeleteDialog = list.id },
                                )
                            }
                        }

                        if (unpinnedLists.isNotEmpty() && pinnedLists.isNotEmpty()) {
                            item {
                                Text(
                                    text = t("shoppingLists.allLists"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 12.dp,
                                        bottom = 4.dp,
                                    ),
                                )
                            }
                        }

                        items(
                            items = unpinnedLists,
                            key = { it.id },
                        ) { list ->
                            SwipeableListCard(
                                listName = list.name,
                                itemCount = list.itemCount,
                                uncheckedCount = list.uncheckedCount,
                                isPinned = false,
                                isPersonal = list.isPersonal,
                                onClick = { onNavigateToDetail(list.id) },
                                onLongClick = {
                                    listsViewModel.togglePin(list.id, list.isPinned)
                                },
                                onDismiss = { showDeleteDialog = list.id },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            households = householdsState.households,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, householdId, isPersonal ->
                listsViewModel.createList(name, householdId, isPersonal)
                showCreateDialog = false
            },
        )
    }

    showDeleteDialog?.let { listId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(t("shoppingListView.deleteList")) },
            text = { Text(t("shoppingLists.confirmDeleteList")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        listsViewModel.deleteList(listId)
                        showDeleteDialog = null
                    },
                ) {
                    Text(t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableListCard(
    listName: String,
    itemCount: Long,
    uncheckedCount: Long,
    isPinned: Boolean,
    isPersonal: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                false
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
                label = "swipe-bg-color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
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
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = t("common.pinned"),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPersonal) t("common.personal") else t("common.shared"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (itemCount > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Badge(
                            containerColor = if (uncheckedCount > 0) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                        ) {
                            Text(
                                text = "$uncheckedCount/$itemCount",
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = t("common.items"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateListDialog(
    households: List<no.shoppinglist.shared.cache.HouseholdEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, householdId: String?, isPersonal: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var isPersonal by rememberSaveable { mutableStateOf(true) }
    var selectedHouseholdIndex by rememberSaveable { mutableStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("nav.newShoppingList")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("createList.listName")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPersonal,
                        onCheckedChange = { isPersonal = it },
                    )
                    Text(t("shoppingLists.personalList"))
                }

                if (!isPersonal && households.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = t("shoppingLists.selectHousehold"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    households.forEachIndexed { index, household ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { selectedHouseholdIndex = index },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedHouseholdIndex == index,
                                onClick = { selectedHouseholdIndex = index },
                            )
                            Text(household.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val householdId = if (!isPersonal && selectedHouseholdIndex >= 0) {
                        households[selectedHouseholdIndex].id
                    } else {
                        null
                    }
                    onCreate(name.trim(), householdId, isPersonal)
                },
                enabled = name.isNotBlank(),
            ) {
                Text(t("common.create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("common.cancel"))
            }
        },
    )
}
