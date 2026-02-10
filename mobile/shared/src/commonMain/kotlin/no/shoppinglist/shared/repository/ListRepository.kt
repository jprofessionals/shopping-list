package no.shoppinglist.shared.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import no.shoppinglist.shared.api.dto.ActivityResponse
import no.shoppinglist.shared.api.dto.CreateItemRequest
import no.shoppinglist.shared.api.dto.CreateListRequest
import no.shoppinglist.shared.api.dto.ItemResponse
import no.shoppinglist.shared.api.dto.ListDetailResponse
import no.shoppinglist.shared.api.dto.SuggestionResponse
import no.shoppinglist.shared.api.dto.UpdateItemRequest
import no.shoppinglist.shared.api.dto.UpdateListRequest
import no.shoppinglist.shared.api.routes.ListApi
import no.shoppinglist.shared.cache.ListItemEntity
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.cache.ShoppingListEntity

class ListRepository(
    private val listApi: ListApi,
    private val database: ShoppingListDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val listQueries = database.shoppingListQueries
    private val itemQueries = database.listItemQueries
    private val syncQueries = database.syncQueueQueries

    val lists: Flow<List<ShoppingListEntity>> =
        listQueries.selectAll().asFlow().mapToList(Dispatchers.Default)

    fun getListItems(listId: String): Flow<List<ListItemEntity>> =
        itemQueries.selectByListId(listId).asFlow().mapToList(Dispatchers.Default)

    suspend fun getAllLists() {
        try {
            val remoteLists = listApi.getLists()
            listQueries.deleteAll()
            for (list in remoteLists) {
                listQueries.insert(
                    id = list.id,
                    name = list.name,
                    householdId = list.householdId,
                    isPersonal = list.isPersonal,
                    createdAt = list.createdAt,
                    isOwner = list.isOwner,
                    itemCount = list.itemCount.toLong(),
                    uncheckedCount = list.uncheckedCount.toLong(),
                    isPinned = list.isPinned,
                )
            }
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            // Offline: rely on cached data
        }
    }

    suspend fun getListDetail(id: String): ListDetailResponse? {
        return try {
            val detail = listApi.getList(id)
            itemQueries.deleteByListId(id)
            for (item in detail.items) {
                insertItemEntity(id, item)
            }
            detail
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            null
        }
    }

    suspend fun createList(
        name: String,
        householdId: String?,
        isPersonal: Boolean,
    ) {
        val request = CreateListRequest(
            name = name,
            householdId = householdId,
            isPersonal = isPersonal,
        )
        try {
            val response = listApi.createList(request)
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
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}"
            listQueries.insert(
                id = tempId,
                name = name,
                householdId = householdId,
                isPersonal = isPersonal,
                createdAt = Clock.System.now().toString(),
                isOwner = true,
                itemCount = 0,
                uncheckedCount = 0,
                isPinned = false,
            )
            val payload = json.encodeToString(CreateListRequest.serializer(), request)
            syncQueries.insert(
                operationType = "CREATE",
                entityType = "LIST",
                entityId = tempId,
                parentId = null,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun updateList(id: String, name: String, isPersonal: Boolean) {
        val request = UpdateListRequest(name = name, isPersonal = isPersonal)
        try {
            val response = listApi.updateList(id, request)
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
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = listQueries.selectById(id).executeAsOneOrNull() ?: return
            listQueries.insert(
                id = existing.id,
                name = name,
                householdId = existing.householdId,
                isPersonal = isPersonal,
                createdAt = existing.createdAt,
                isOwner = existing.isOwner,
                itemCount = existing.itemCount,
                uncheckedCount = existing.uncheckedCount,
                isPinned = existing.isPinned,
            )
            val payload = json.encodeToString(UpdateListRequest.serializer(), request)
            syncQueries.insert(
                operationType = "UPDATE",
                entityType = "LIST",
                entityId = id,
                parentId = null,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun deleteList(id: String) {
        try {
            listApi.deleteList(id)
            listQueries.deleteById(id)
            itemQueries.deleteByListId(id)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            listQueries.deleteById(id)
            itemQueries.deleteByListId(id)
            syncQueries.insert(
                operationType = "DELETE",
                entityType = "LIST",
                entityId = id,
                parentId = null,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun addItem(
        listId: String,
        name: String,
        quantity: Double,
        unit: String?,
    ) {
        val request = CreateItemRequest(name = name, quantity = quantity, unit = unit)
        try {
            val response = listApi.addItem(listId, request)
            insertItemEntity(listId, response)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}"
            itemQueries.insert(
                id = tempId,
                listId = listId,
                name = name,
                quantity = quantity,
                unit = unit,
                isChecked = false,
                checkedByName = null,
                createdAt = Clock.System.now().toString(),
            )
            val payload = json.encodeToString(CreateItemRequest.serializer(), request)
            syncQueries.insert(
                operationType = "CREATE",
                entityType = "ITEM",
                entityId = tempId,
                parentId = listId,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun updateItem(
        listId: String,
        itemId: String,
        name: String,
        quantity: Double,
        unit: String?,
    ) {
        val request = UpdateItemRequest(name = name, quantity = quantity, unit = unit)
        try {
            val response = listApi.updateItem(listId, itemId, request)
            insertItemEntity(listId, response)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = itemQueries.selectById(itemId).executeAsOneOrNull() ?: return
            itemQueries.insert(
                id = existing.id,
                listId = existing.listId,
                name = name,
                quantity = quantity,
                unit = unit,
                isChecked = existing.isChecked,
                checkedByName = existing.checkedByName,
                createdAt = existing.createdAt,
            )
            val payload = json.encodeToString(UpdateItemRequest.serializer(), request)
            syncQueries.insert(
                operationType = "UPDATE",
                entityType = "ITEM",
                entityId = itemId,
                parentId = listId,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun deleteItem(listId: String, itemId: String) {
        try {
            listApi.deleteItem(listId, itemId)
            itemQueries.deleteById(itemId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            itemQueries.deleteById(itemId)
            syncQueries.insert(
                operationType = "DELETE",
                entityType = "ITEM",
                entityId = itemId,
                parentId = listId,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun toggleCheck(listId: String, itemId: String) {
        try {
            val response = listApi.toggleCheck(listId, itemId)
            insertItemEntity(listId, response)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = itemQueries.selectById(itemId).executeAsOneOrNull() ?: return
            itemQueries.insert(
                id = existing.id,
                listId = existing.listId,
                name = existing.name,
                quantity = existing.quantity,
                unit = existing.unit,
                isChecked = !existing.isChecked,
                checkedByName = existing.checkedByName,
                createdAt = existing.createdAt,
            )
            syncQueries.insert(
                operationType = "TOGGLE",
                entityType = "ITEM",
                entityId = itemId,
                parentId = listId,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun clearChecked(listId: String) {
        try {
            val response = listApi.clearChecked(listId)
            for (deletedId in response.deletedItemIds) {
                itemQueries.deleteById(deletedId)
            }
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            itemQueries.deleteCheckedByListId(listId)
            syncQueries.insert(
                operationType = "CLEAR_CHECKED",
                entityType = "LIST",
                entityId = listId,
                parentId = null,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun pinList(id: String) {
        try {
            listApi.pinList(id)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
        updatePinLocally(id, pinned = true)
    }

    suspend fun unpinList(id: String) {
        try {
            listApi.unpinList(id)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
        updatePinLocally(id, pinned = false)
    }

    private fun updatePinLocally(id: String, pinned: Boolean) {
        val existing = listQueries.selectById(id).executeAsOneOrNull() ?: return
        listQueries.insert(
            id = existing.id,
            name = existing.name,
            householdId = existing.householdId,
            isPersonal = existing.isPersonal,
            createdAt = existing.createdAt,
            isOwner = existing.isOwner,
            itemCount = existing.itemCount,
            uncheckedCount = existing.uncheckedCount,
            isPinned = pinned,
        )
    }

    suspend fun getSuggestions(query: String, limit: Int = 10): List<SuggestionResponse> =
        listApi.getSuggestions(query, limit)

    suspend fun getActivity(listId: String, limit: Int = 20): List<ActivityResponse> =
        listApi.getActivity(listId, limit)

    private fun insertItemEntity(listId: String, item: ItemResponse) {
        itemQueries.insert(
            id = item.id,
            listId = listId,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            isChecked = item.isChecked,
            checkedByName = item.checkedByName,
            createdAt = item.createdAt,
        )
    }
}

/**
 * Determines whether an exception represents a network connectivity error.
 * In KMP, Ktor wraps network errors in various platform-specific exception types,
 * but they typically contain "connect", "timeout", or "unreachable" in their message
 * or are instances of common I/O error patterns.
 */
internal fun Exception.isNetworkError(): Boolean {
    val name = this::class.simpleName ?: ""
    val msg = message?.lowercase() ?: ""
    return name.contains("IOException", ignoreCase = true) ||
        name.contains("ConnectException", ignoreCase = true) ||
        name.contains("SocketException", ignoreCase = true) ||
        name.contains("UnresolvedAddress", ignoreCase = true) ||
        name.contains("TimeoutException", ignoreCase = true) ||
        name.contains("HttpRequestTimeoutException", ignoreCase = true) ||
        msg.contains("unable to resolve host") ||
        msg.contains("connect timed out") ||
        msg.contains("failed to connect") ||
        msg.contains("network is unreachable") ||
        msg.contains("no route to host")
}
