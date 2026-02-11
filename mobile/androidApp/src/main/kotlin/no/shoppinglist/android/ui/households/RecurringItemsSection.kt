package no.shoppinglist.android.ui.households

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.shoppinglist.android.i18n.t
import no.shoppinglist.android.viewmodel.RecurringItemsViewModel
import no.shoppinglist.shared.cache.RecurringItemEntity
import org.koin.androidx.compose.koinViewModel

private val frequencies = listOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY")
private val frequencyLabelKeys = listOf(
    "recurring.frequencyDaily",
    "recurring.frequencyWeekly",
    "recurring.frequencyBiweekly",
    "recurring.frequencyMonthly",
)

@Composable
fun RecurringItemsSection(
    householdId: String,
    recurringItemsViewModel: RecurringItemsViewModel = koinViewModel(),
) {
    val uiState by recurringItemsViewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringItemEntity?>(null) }
    var pausingItem by remember { mutableStateOf<RecurringItemEntity?>(null) }
    var deletingItem by remember { mutableStateOf<RecurringItemEntity?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = t("recurring.title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = t("recurring.addItem"))
            }
        }

        if (uiState.items.isEmpty()) {
            Text(
                text = t("recurring.noItems"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            uiState.items.forEach { item ->
                RecurringItemCard(
                    item = item,
                    onEdit = { editingItem = item },
                    onPause = { pausingItem = item },
                    onResume = { recurringItemsViewModel.resume(item.id) },
                    onDelete = { deletingItem = item },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showCreateDialog) {
        RecurringItemFormDialog(
            title = t("recurring.newItem"),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, quantity, unit, frequency ->
                recurringItemsViewModel.create(name, quantity, unit, frequency)
                showCreateDialog = false
            },
        )
    }

    editingItem?.let { item ->
        RecurringItemFormDialog(
            title = t("recurring.editItem"),
            initialName = item.name,
            initialQuantity = item.quantity,
            initialUnit = item.unit,
            initialFrequency = item.frequency,
            onDismiss = { editingItem = null },
            onConfirm = { name, quantity, unit, frequency ->
                recurringItemsViewModel.update(item.id, name, quantity, unit, frequency)
                editingItem = null
            },
        )
    }

    pausingItem?.let { item ->
        PauseDialog(
            onDismiss = { pausingItem = null },
            onPause = { until ->
                recurringItemsViewModel.pause(item.id, until)
                pausingItem = null
            },
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(t("recurring.confirmDelete")) },
            text = { Text(t("recurring.confirmDeleteDescription")) },
            confirmButton = {
                TextButton(onClick = {
                    recurringItemsViewModel.delete(item.id)
                    deletingItem = null
                }) {
                    Text(t("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(t("common.cancel"))
                }
            },
        )
    }
}

@Composable
private fun RecurringItemCard(
    item: RecurringItemEntity,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val freqIndex = frequencies.indexOf(item.frequency)
    val freqLabel = if (freqIndex >= 0) t(frequencyLabelKeys[freqIndex]) else item.frequency

    ElevatedCard(
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
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val qtyText = if (item.quantity == item.quantity.toLong().toDouble()) {
                        item.quantity.toLong().toString()
                    } else {
                        item.quantity.toString()
                    }
                    Text(
                        text = if (item.unit != null) "$qtyText ${item.unit}" else qtyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!item.isActive) {
                    val pauseText = if (item.pausedUntil != null) {
                        t("recurring.pausedUntilDate", "date" to item.pausedUntil!!)
                    } else {
                        t("recurring.paused")
                    }
                    Text(
                        text = pauseText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                item.lastPurchased?.let { date ->
                    Text(
                        text = t("recurring.lastPurchased", "date" to date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = t("common.moreOptions"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(t("recurring.edit")) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    )
                    if (item.isActive) {
                        DropdownMenuItem(
                            text = { Text(t("recurring.pause")) },
                            onClick = {
                                showMenu = false
                                onPause()
                            },
                            leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(t("recurring.resume")) },
                            onClick = {
                                showMenu = false
                                onResume()
                            },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(t("common.delete"), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringItemFormDialog(
    title: String,
    initialName: String = "",
    initialQuantity: Double = 1.0,
    initialUnit: String? = null,
    initialFrequency: String = "WEEKLY",
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Double, unit: String?, frequency: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var quantityText by rememberSaveable {
        mutableStateOf(
            if (initialQuantity == initialQuantity.toLong().toDouble()) {
                initialQuantity.toLong().toString()
            } else {
                initialQuantity.toString()
            },
        )
    }
    var unit by rememberSaveable { mutableStateOf(initialUnit ?: "") }
    var selectedFrequency by rememberSaveable { mutableStateOf(initialFrequency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("common.name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(t("shoppingListView.qty")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(t("shoppingListView.unit")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Frequency selector
                Text(
                    text = t("recurring.frequency"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequencies.forEachIndexed { index, freq ->
                        FilterChip(
                            selected = selectedFrequency == freq,
                            onClick = { selectedFrequency = freq },
                            label = {
                                Text(
                                    t(frequencyLabelKeys[index]),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                    onConfirm(name.trim(), qty, unit.ifBlank { null }, selectedFrequency)
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

@Composable
private fun PauseDialog(
    onDismiss: () -> Unit,
    onPause: (until: String?) -> Unit,
) {
    var untilDate by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("recurring.pauseTitle")) },
        text = {
            Column {
                OutlinedTextField(
                    value = untilDate,
                    onValueChange = { untilDate = it },
                    label = { Text(t("recurring.pauseUntilLabel")) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = t("recurring.pauseUntilHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPause(untilDate.ifBlank { null })
            }) {
                Text(t("recurring.pause"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("common.cancel"))
            }
        },
    )
}
