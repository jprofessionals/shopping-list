package no.shoppinglist.android.ui.households

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import no.shoppinglist.android.ui.comments.CommentsSection
import no.shoppinglist.android.ui.common.LoadingScreen
import no.shoppinglist.android.viewmodel.HouseholdDetailViewModel
import no.shoppinglist.shared.api.dto.MemberResponse
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdDetailScreen(
    householdId: String,
    onNavigateBack: () -> Unit,
    householdDetailViewModel: HouseholdDetailViewModel = koinViewModel(),
) {
    val uiState by householdDetailViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    var showAddMemberDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveMemberDialog by remember { mutableStateOf<MemberResponse?>(null) }

    val isOwner = uiState.isOwner

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    if (uiState.isLoading && uiState.members.isEmpty()) {
        LoadingScreen()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.name,
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
                    IconButton(onClick = { showEditNameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = t("householdDetail.editNameAction"))
                    }
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = t("householdDetail.addMemberAction"))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = t("householdDetail.members", "count" to uiState.members.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            items(
                items = uiState.members,
                key = { it.accountId },
            ) { member ->
                val isSelf = member.accountId == uiState.currentUserId
                MemberRow(
                    member = member,
                    onChangeRole = { newRole ->
                        householdDetailViewModel.updateMemberRole(member.accountId, newRole)
                    },
                    onRemove = { showRemoveMemberDialog = member },
                    canChangeRole = isOwner,
                    canRemove = isOwner && !isSelf,
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }

            item {
                CommentsSection(
                    targetType = "household",
                    targetId = householdId,
                )
            }
        }
    }

    if (showEditNameDialog) {
        var newName by rememberSaveable { mutableStateOf(uiState.name) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(t("householdDetail.editName")) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(t("householdDetail.name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        householdDetailViewModel.updateName(newName.trim())
                        showEditNameDialog = false
                    },
                    enabled = newName.isNotBlank(),
                ) {
                    Text(t("common.save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onAdd = { email, role ->
                householdDetailViewModel.addMember(email, role)
                showAddMemberDialog = false
            },
        )
    }

    showRemoveMemberDialog?.let { member ->
        AlertDialog(
            onDismissRequest = { showRemoveMemberDialog = null },
            title = { Text(t("householdDetail.removeMember")) },
            text = {
                Text(t("householdDetail.confirmRemoveMember", "name" to "${member.displayName} (${member.email})"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        householdDetailViewModel.removeMember(member.accountId)
                        showRemoveMemberDialog = null
                    },
                ) {
                    Text(t("common.remove"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveMemberDialog = null }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberResponse,
    onChangeRole: (String) -> Unit,
    onRemove: () -> Unit,
    canChangeRole: Boolean,
    canRemove: Boolean,
) {
    var showRoleMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = member.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                AssistChip(
                    onClick = { if (canChangeRole) showRoleMenu = true },
                    label = {
                        Text(
                            text = if (member.role == "OWNER") t("householdDetail.roleOwner") else t("householdDetail.roleMember"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                if (canChangeRole) {
                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false },
                    ) {
                        listOf("MEMBER", "OWNER").forEach { role ->
                            DropdownMenuItem(
                                text = { Text(if (role == "OWNER") t("householdDetail.roleOwner") else t("householdDetail.roleMember")) },
                                onClick = {
                                    onChangeRole(role)
                                    showRoleMenu = false
                                },
                            )
                        }
                        if (canRemove) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(t("common.remove"), color = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showRoleMenu = false
                                    onRemove()
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
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (email: String, role: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("MEMBER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("householdDetail.addMember")) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(t("householdDetail.emailLabel")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = t("householdDetail.roleLabel"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MEMBER", "OWNER").forEach { role ->
                        androidx.compose.material3.FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(if (role == "OWNER") t("householdDetail.roleOwner") else t("householdDetail.roleMember")) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(email.trim(), selectedRole) },
                enabled = email.isNotBlank(),
            ) {
                Text(t("common.add"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("common.cancel"))
            }
        },
    )
}
