package no.shoppinglist.shared.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import no.shoppinglist.shared.api.dto.CreateRecurringItemRequest
import no.shoppinglist.shared.api.dto.PauseRecurringItemRequest
import no.shoppinglist.shared.api.dto.RecurringItemResponse
import no.shoppinglist.shared.api.dto.UpdateRecurringItemRequest
import no.shoppinglist.shared.api.routes.RecurringItemApi
import no.shoppinglist.shared.cache.RecurringItemEntity
import no.shoppinglist.shared.cache.ShoppingListDatabase

class RecurringItemRepository(
    private val recurringItemApi: RecurringItemApi,
    private val database: ShoppingListDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val recurringQueries = database.recurringItemQueries
    private val syncQueries = database.syncQueueQueries

    fun getByHousehold(householdId: String): Flow<List<RecurringItemEntity>> =
        recurringQueries.selectByHouseholdId(householdId).asFlow().mapToList(Dispatchers.Default)

    suspend fun fetchAll(householdId: String) {
        try {
            val items = recurringItemApi.getItems(householdId)
            recurringQueries.deleteByHouseholdId(householdId)
            for (item in items) {
                insertEntity(item, householdId)
            }
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
    }

    suspend fun create(
        householdId: String,
        name: String,
        quantity: Double,
        unit: String?,
        frequency: String,
    ) {
        val request = CreateRecurringItemRequest(
            name = name,
            quantity = quantity,
            unit = unit,
            frequency = frequency,
        )
        try {
            val response = recurringItemApi.createItem(householdId, request)
            insertEntity(response, householdId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}"
            recurringQueries.insert(
                id = tempId,
                householdId = householdId,
                name = name,
                quantity = quantity,
                unit = unit,
                frequency = frequency,
                lastPurchased = null,
                isActive = true,
                pausedUntil = null,
                createdById = "",
                createdByName = "",
            )
            val payload = json.encodeToString(CreateRecurringItemRequest.serializer(), request)
            syncQueries.insert(
                operationType = "CREATE",
                entityType = "RECURRING_ITEM",
                entityId = tempId,
                parentId = householdId,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun update(
        householdId: String,
        itemId: String,
        name: String,
        quantity: Double,
        unit: String?,
        frequency: String,
    ) {
        val request = UpdateRecurringItemRequest(
            name = name,
            quantity = quantity,
            unit = unit,
            frequency = frequency,
        )
        try {
            val response = recurringItemApi.updateItem(householdId, itemId, request)
            insertEntity(response, householdId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = recurringQueries.selectById(itemId).executeAsOneOrNull() ?: return
            recurringQueries.insert(
                id = existing.id,
                householdId = existing.householdId,
                name = name,
                quantity = quantity,
                unit = unit,
                frequency = frequency,
                lastPurchased = existing.lastPurchased,
                isActive = existing.isActive,
                pausedUntil = existing.pausedUntil,
                createdById = existing.createdById,
                createdByName = existing.createdByName,
            )
            val payload = json.encodeToString(UpdateRecurringItemRequest.serializer(), request)
            syncQueries.insert(
                operationType = "UPDATE",
                entityType = "RECURRING_ITEM",
                entityId = itemId,
                parentId = householdId,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun delete(householdId: String, itemId: String) {
        try {
            recurringItemApi.deleteItem(householdId, itemId)
            recurringQueries.deleteById(itemId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            recurringQueries.deleteById(itemId)
            syncQueries.insert(
                operationType = "DELETE",
                entityType = "RECURRING_ITEM",
                entityId = itemId,
                parentId = householdId,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun pause(householdId: String, itemId: String, until: String?) {
        val request = PauseRecurringItemRequest(until = until)
        try {
            val response = recurringItemApi.pauseItem(householdId, itemId, request)
            insertEntity(response, householdId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = recurringQueries.selectById(itemId).executeAsOneOrNull() ?: return
            recurringQueries.insert(
                id = existing.id,
                householdId = existing.householdId,
                name = existing.name,
                quantity = existing.quantity,
                unit = existing.unit,
                frequency = existing.frequency,
                lastPurchased = existing.lastPurchased,
                isActive = false,
                pausedUntil = until,
                createdById = existing.createdById,
                createdByName = existing.createdByName,
            )
            val payload = json.encodeToString(PauseRecurringItemRequest.serializer(), request)
            syncQueries.insert(
                operationType = "PAUSE",
                entityType = "RECURRING_ITEM",
                entityId = itemId,
                parentId = householdId,
                payload = payload,
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    suspend fun resume(householdId: String, itemId: String) {
        try {
            val response = recurringItemApi.resumeItem(householdId, itemId)
            insertEntity(response, householdId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            val existing = recurringQueries.selectById(itemId).executeAsOneOrNull() ?: return
            recurringQueries.insert(
                id = existing.id,
                householdId = existing.householdId,
                name = existing.name,
                quantity = existing.quantity,
                unit = existing.unit,
                frequency = existing.frequency,
                lastPurchased = existing.lastPurchased,
                isActive = true,
                pausedUntil = null,
                createdById = existing.createdById,
                createdByName = existing.createdByName,
            )
            syncQueries.insert(
                operationType = "RESUME",
                entityType = "RECURRING_ITEM",
                entityId = itemId,
                parentId = householdId,
                payload = "{}",
                createdAt = Clock.System.now().toString(),
            )
        }
    }

    private fun insertEntity(response: RecurringItemResponse, householdId: String) {
        recurringQueries.insert(
            id = response.id,
            householdId = householdId,
            name = response.name,
            quantity = response.quantity,
            unit = response.unit,
            frequency = response.frequency,
            lastPurchased = response.lastPurchased,
            isActive = response.isActive,
            pausedUntil = response.pausedUntil,
            createdById = response.createdBy.id,
            createdByName = response.createdBy.displayName,
        )
    }
}
