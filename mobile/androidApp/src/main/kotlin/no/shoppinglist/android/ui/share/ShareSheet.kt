package no.shoppinglist.android.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import no.shoppinglist.android.i18n.LocalI18n
import no.shoppinglist.android.i18n.t
import no.shoppinglist.android.viewmodel.ShareViewModel
import no.shoppinglist.shared.api.dto.ShareResponse
import org.koin.androidx.compose.koinViewModel

private val permissions = listOf("READ", "CHECK", "WRITE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    listId: String,
    onDismiss: () -> Unit,
    shareViewModel: ShareViewModel = koinViewModel(),
) {
    val uiState by shareViewModel.uiState.collectAsStateWithLifecycle()
    val i18n = LocalI18n.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(listId) {
        shareViewModel.loadShares(listId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = t("shareList.title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SnackbarHost(snackbarHostState)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Existing shares
            if (uiState.shares.isNotEmpty()) {
                item {
                    Text(
                        text = t("shareList.currentShares"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                items(
                    items = uiState.shares,
                    key = { it.id },
                ) { share ->
                    ShareRow(
                        share = share,
                        onDelete = { shareViewModel.deleteShare(listId, share.id) },
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // New share link display
            uiState.newShareLink?.let { link ->
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t("shareList.shareLinkCreated"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = link,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", link))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(i18n.t("shareList.linkCopied"))
                                    }
                                    shareViewModel.clearNewShareLink()
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = t("shareList.copyLink"),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Share with user section
            item {
                ShareWithUserSection(
                    onShare = { email, permission ->
                        shareViewModel.createUserShare(listId, permission, email)
                    },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Create link section
            item {
                CreateLinkSection(
                    onCreate = { permission, days ->
                        shareViewModel.createLinkShare(listId, permission, days)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareRow(
    share: ShareResponse,
    onDelete: () -> Unit,
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
                label = "share-swipe-color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("shareList.deleteShare"),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (share.type == "USER") Icons.Default.Person else Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (share.type) {
                            "USER" -> share.accountEmail ?: t("shareList.userShareLabel")
                            else -> t("shareList.linkShareLabel")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row {
                        Text(
                            text = share.permission,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        share.expiresAt?.let { expires ->
                            Text(
                                text = " - " + t("shareList.expires", "date" to expires),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = t("shareList.deleteShare"),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareWithUserSection(
    onShare: (email: String, permission: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var selectedPermission by rememberSaveable { mutableStateOf("READ") }
    var permissionExpanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("shareList.shareWithUser"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(t("householdDetail.emailLabel")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = permissionExpanded,
                onExpandedChange = { permissionExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedPermission,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(t("userShare.permission")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = permissionExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = permissionExpanded,
                    onDismissRequest = { permissionExpanded = false },
                ) {
                    permissions.forEach { perm ->
                        DropdownMenuItem(
                            text = { Text(perm) },
                            onClick = {
                                selectedPermission = perm
                                permissionExpanded = false
                            },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onShare(email.trim(), selectedPermission)
                    email = ""
                },
                enabled = email.isNotBlank(),
            ) {
                Text(t("userShare.shareButton"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLinkSection(
    onCreate: (permission: String, expirationDays: Int) -> Unit,
) {
    var selectedPermission by rememberSaveable { mutableStateOf("READ") }
    var expirationDays by rememberSaveable { mutableIntStateOf(7) }
    var permissionExpanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("shareList.createShareLink"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = permissionExpanded,
                onExpandedChange = { permissionExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedPermission,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(t("linkShare.permission")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = permissionExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = permissionExpanded,
                    onDismissRequest = { permissionExpanded = false },
                ) {
                    permissions.forEach { perm ->
                        DropdownMenuItem(
                            text = { Text(perm) },
                            onClick = {
                                selectedPermission = perm
                                permissionExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = expirationDays.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { if (it in 1..365) expirationDays = it }
                },
                label = { Text(t("shareList.days")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onCreate(selectedPermission, expirationDays) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(t("linkShare.createLink"))
        }
    }
}
