package no.shoppinglist.shared.sync

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.shoppinglist.shared.api.dto.CreateItemRequest
import no.shoppinglist.shared.api.dto.CreateListRequest
import no.shoppinglist.shared.api.dto.UpdateItemRequest
import no.shoppinglist.shared.api.dto.UpdateListRequest
import no.shoppinglist.shared.api.routes.ListApi
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.cache.SyncQueueEntry
import no.shoppinglist.shared.repository.isNetworkError

/**
 * Callback invoked when a sync conflict or permanent failure occurs.
 * The message describes what happened (e.g., "Your change to 'Milk' was overridden").
 */
typealias SyncNotificationCallback = (message: String) -> Unit

class SyncManager(
    private val database: ShoppingListDatabase,
    private val listApi: ListApi,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val onNotification: SyncNotificationCallback = {},
) {
    private val syncQueries = database.syncQueueQueries
    private val listQueries = database.shoppingListQueries
    private val itemQueries = database.listItemQueries

    /**
     * Drains the sync queue in order, attempting to replay each pending change
     * against the server. Handles conflicts, retries, and permanent failures.
     */
    suspend fun syncPendingChanges() {
        val pendingEntries = syncQueries.selectPending().executeAsList()

        for (entry in pendingEntries) {
            try {
                processEntry(entry)
                syncQueries.deleteById(entry.id)
            } catch (e: ClientRequestException) {
                val statusCode = e.response.status
                if (statusCode == HttpStatusCode.NotFound ||
                    statusCode == HttpStatusCode.Conflict
                ) {
                    // Conflict or resource gone: discard the entry and refresh from server
                    syncQueries.deleteById(entry.id)
                    refreshFromServer(entry)
                    onNotification("Your change to '${describeEntry(entry)}' was overridden")
                } else {
                    // Other server errors: increment retry
                    handleRetryOrRemove(entry)
                }
            } catch (e: Exception) {
                if (e.isNetworkError()) {
                    handleRetryOrRemove(entry)
                    // Stop processing on network error - no point trying the rest
                    break
                }
                handleRetryOrRemove(entry)
            }
        }
    }

    private suspend fun processEntry(entry: SyncQueueEntry) {
        when (entry.entityType) {
            "LIST" -> processListEntry(entry)
            "ITEM" -> processItemEntry(entry)
            else -> {
                // Unknown entity type; remove from queue
                syncQueries.deleteById(entry.id)
            }
        }
    }

    private suspend fun processListEntry(entry: SyncQueueEntry) {
        when (entry.operationType) {
            "CREATE" -> {
                val request = json.decodeFromString(CreateListRequest.serializer(), entry.payload)
                val response = listApi.createList(request)
                // Replace the temp entry with the real one
                listQueries.deleteById(entry.entityId)
                listQueries.insert(
                    id = response.id,
                    name = response.name,
                    householdId = response.householdId,
                    isPersonal = response.isPersonal,
                    createdAt = response.createdAt,
                    isOwner = response.isOwner,
                    itemCount = response.itemCount.toLong(),
                    uncheckedCount = response.uncheckedCount.toLong(),
                    isPinned = response.isPinned,
                )
            }
            "UPDATE" -> {
                val request = json.decodeFromString(UpdateListRequest.serializer(), entry.payload)
                val response = listApi.updateList(entry.entityId, request)
                listQueries.insert(
                    id = response.id,
                    name = response.name,
                    householdId = response.householdId,
                    isPersonal = response.isPersonal,
                    createdAt = response.createdAt,
                    isOwner = response.isOwner,
                    itemCount = response.itemCount.toLong(),
                    uncheckedCount = response.uncheckedCount.toLong(),
                    isPinned = response.isPinned,
                )
            }
            "DELETE" -> {
                listApi.deleteList(entry.entityId)
            }
            "CLEAR_CHECKED" -> {
                listApi.clearChecked(entry.entityId)
            }
        }
    }

    private suspend fun processItemEntry(entry: SyncQueueEntry) {
        val parentId = entry.parentId ?: return

        when (entry.operationType) {
            "CREATE" -> {
                val request = json.decodeFromString(CreateItemRequest.serializer(), entry.payload)
                val response = listApi.addItem(parentId, request)
                // Replace the temp item with the real one
                itemQueries.deleteById(entry.entityId)
                itemQueries.insert(
                    id = response.id,
                    listId = parentId,
                    name = response.name,
                    quantity = response.quantity,
                    unit = response.unit,
                    isChecked = response.isChecked,
                    checkedByName = response.checkedByName,
                    createdAt = response.createdAt,
                )
            }
            "UPDATE" -> {
                val request = json.decodeFromString(UpdateItemRequest.serializer(), entry.payload)
                val response = listApi.updateItem(parentId, entry.entityId, request)
                itemQueries.insert(
                    id = response.id,
                    listId = parentId,
                    name = response.name,
                    quantity = response.quantity,
                    unit = response.unit,
                    isChecked = response.isChecked,
                    checkedByName = response.checkedByName,
                    createdAt = response.createdAt,
                )
            }
            "DELETE" -> {
                listApi.deleteItem(parentId, entry.entityId)
            }
            "TOGGLE" -> {
                val response = listApi.toggleCheck(parentId, entry.entityId)
                itemQueries.insert(
                    id = response.id,
                    listId = parentId,
                    name = response.name,
                    quantity = response.quantity,
                    unit = response.unit,
                    isChecked = response.isChecked,
                    checkedByName = response.checkedByName,
                    createdAt = response.createdAt,
                )
            }
        }
    }

    private suspend fun refreshFromServer(entry: SyncQueueEntry) {
        try {
            when (entry.entityType) {
                "LIST" -> {
                    if (entry.operationType != "DELETE") {
                        try {
                            val detail = listApi.getList(entry.entityId)
                            listQueries.insert(
                                id = detail.id,
                                name = detail.name,
                                householdId = detail.householdId,
                                isPersonal = detail.isPersonal,
                                createdAt = detail.createdAt,
                                isOwner = detail.isOwner,
                                itemCount = detail.items.size.toLong(),
                                uncheckedCount = detail.items.count { !it.isChecked }.toLong(),
                                isPinned = detail.isPinned,
                            )
                            // Refresh items too
                            itemQueries.deleteByListId(entry.entityId)
                            for (item in detail.items) {
                                itemQueries.insert(
                                    id = item.id,
                                    listId = entry.entityId,
                                    name = item.name,
                                    quantity = item.quantity,
                                    unit = item.unit,
                                    isChecked = item.isChecked,
                                    checkedByName = item.checkedByName,
                                    createdAt = item.createdAt,
                                )
                            }
                        } catch (_: ClientRequestException) {
                            // List was deleted on server
                            listQueries.deleteById(entry.entityId)
                            itemQueries.deleteByListId(entry.entityId)
                        }
                    }
                }
                "ITEM" -> {
                    val parentId = entry.parentId ?: return
                    try {
                        val detail = listApi.getList(parentId)
                        itemQueries.deleteByListId(parentId)
                        for (item in detail.items) {
                            itemQueries.insert(
                                id = item.id,
                                listId = parentId,
                                name = item.name,
                                quantity = item.quantity,
                                unit = item.unit,
                                isChecked = item.isChecked,
                                checkedByName = item.checkedByName,
                                createdAt = item.createdAt,
                            )
                        }
                    } catch (_: ClientRequestException) {
                        // Parent list was deleted
                        itemQueries.deleteByListId(parentId)
                    }
                }
            }
        } catch (_: Exception) {
            // If refresh also fails, we just leave local state as-is
        }
    }

    private fun handleRetryOrRemove(entry: SyncQueueEntry) {
        if (entry.retryCount >= 2) {
            // Will become 3 after increment, so this is the last allowed attempt
            syncQueries.deleteById(entry.id)
            onNotification("Failed to sync change to '${describeEntry(entry)}' after multiple attempts")
        } else {
            syncQueries.incrementRetry(entry.id)
        }
    }

    private fun describeEntry(entry: SyncQueueEntry): String {
        return try {
            when {
                entry.entityType == "LIST" && entry.operationType == "CREATE" -> {
                    val request = json.decodeFromString(CreateListRequest.serializer(), entry.payload)
                    request.name
                }
                entry.entityType == "LIST" && entry.operationType == "UPDATE" -> {
                    val request = json.decodeFromString(UpdateListRequest.serializer(), entry.payload)
                    request.name
                }
                entry.entityType == "ITEM" && entry.operationType == "CREATE" -> {
                    val request = json.decodeFromString(CreateItemRequest.serializer(), entry.payload)
                    request.name
                }
                entry.entityType == "ITEM" && entry.operationType == "UPDATE" -> {
                    val request = json.decodeFromString(UpdateItemRequest.serializer(), entry.payload)
                    request.name
                }
                else -> entry.entityId
            }
        } catch (_: Exception) {
            entry.entityId
        }
    }
}
