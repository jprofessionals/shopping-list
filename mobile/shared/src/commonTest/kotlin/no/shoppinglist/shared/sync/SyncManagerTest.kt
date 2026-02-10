package no.shoppinglist.shared.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import no.shoppinglist.shared.api.dto.ItemResponse
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.repository.TestListApi
import no.shoppinglist.shared.test.createTestDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncManagerTest {

    private lateinit var database: ShoppingListDatabase
    private val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val notifications = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        notifications.clear()
    }

    private fun createTestListApi(mockEngine: MockEngine): TestListApi {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            defaultRequest { url("http://localhost:8080") }
            expectSuccess = true
        }
        return TestListApi(httpClient, testJson)
    }

    @Test
    fun pendingEntriesAreDrainedInOrder() = runTest {
        // Insert two sync entries
        database.syncQueueQueries.insert(
            operationType = "CREATE",
            entityType = "ITEM",
            entityId = "temp_1",
            parentId = "list-1",
            payload = """{"name":"Milk","quantity":1.0}""",
            createdAt = "2025-01-01T00:00:00Z",
        )
        database.syncQueueQueries.insert(
            operationType = "CREATE",
            entityType = "ITEM",
            entityId = "temp_2",
            parentId = "list-1",
            payload = """{"name":"Bread","quantity":2.0}""",
            createdAt = "2025-01-01T00:01:00Z",
        )

        val processedPaths = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            processedPaths.add(request.url.encodedPath)
            val index = processedPaths.size
            val itemResponse = ItemResponse(
                id = "real-$index",
                name = if (index == 1) "Milk" else "Bread",
                quantity = if (index == 1) 1.0 else 2.0,
                unit = null,
                isChecked = false,
                checkedByName = null,
                createdAt = "2025-01-01T00:00:00Z",
            )
            respond(
                content = testJson.encodeToString(ItemResponse.serializer(), itemResponse),
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val listApi = createTestListApi(mockEngine)
        val syncManager = SyncManager(
            database = database,
            listApi = listApi,
            json = testJson,
            onNotification = { notifications.add(it) },
        )

        syncManager.syncPendingChanges()

        // Both entries should have been processed
        assertEquals(2, processedPaths.size)
        assertTrue(processedPaths.all { it.contains("/items") })

        // Sync queue should be empty
        val remaining = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(0, remaining.size)
    }

    @Test
    fun on404EntryIsRemovedAndNotificationCallbackIsCalled() = runTest {
        database.syncQueueQueries.insert(
            operationType = "UPDATE",
            entityType = "ITEM",
            entityId = "item-deleted",
            parentId = "list-1",
            payload = """{"name":"Milk","quantity":1.0,"unit":null}""",
            createdAt = "2025-01-01T00:00:00Z",
        )

        val mockEngine = MockEngine { _ ->
            respondError(HttpStatusCode.NotFound)
        }

        val listApi = createTestListApi(mockEngine)
        val syncManager = SyncManager(
            database = database,
            listApi = listApi,
            json = testJson,
            onNotification = { notifications.add(it) },
        )

        syncManager.syncPendingChanges()

        // Entry should have been removed from queue
        val remaining = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(0, remaining.size)

        // Notification should have been sent about the conflict
        assertEquals(1, notifications.size)
        assertTrue(notifications[0].contains("overridden"))
    }

    @Test
    fun on409EntryIsRemovedAndNotificationCallbackIsCalled() = runTest {
        database.syncQueueQueries.insert(
            operationType = "UPDATE",
            entityType = "LIST",
            entityId = "list-1",
            parentId = null,
            payload = """{"name":"Updated Name","isPersonal":false}""",
            createdAt = "2025-01-01T00:00:00Z",
        )

        val mockEngine = MockEngine { _ ->
            respondError(HttpStatusCode.Conflict)
        }

        val listApi = createTestListApi(mockEngine)
        val syncManager = SyncManager(
            database = database,
            listApi = listApi,
            json = testJson,
            onNotification = { notifications.add(it) },
        )

        syncManager.syncPendingChanges()

        val remaining = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(0, remaining.size)

        assertEquals(1, notifications.size)
        assertTrue(notifications[0].contains("overridden"))
    }

    @Test
    fun retryCountIncrementsOnNetworkError() = runTest {
        database.syncQueueQueries.insert(
            operationType = "CREATE",
            entityType = "ITEM",
            entityId = "temp_1",
            parentId = "list-1",
            payload = """{"name":"Milk","quantity":1.0}""",
            createdAt = "2025-01-01T00:00:00Z",
        )

        val mockEngine = MockEngine { _ ->
            throw Exception("failed to connect")
        }

        val listApi = createTestListApi(mockEngine)
        val syncManager = SyncManager(
            database = database,
            listApi = listApi,
            json = testJson,
            onNotification = { notifications.add(it) },
        )

        // First sync attempt
        syncManager.syncPendingChanges()

        var entries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(1, entries.size)
        assertEquals(1L, entries[0].retryCount)

        // Second sync attempt
        syncManager.syncPendingChanges()

        entries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(1, entries.size)
        assertEquals(2L, entries[0].retryCount)
    }

    @Test
    fun entriesExceedingMaxRetriesAreRemovedWithNotification() = runTest {
        database.syncQueueQueries.insert(
            operationType = "CREATE",
            entityType = "ITEM",
            entityId = "temp_1",
            parentId = "list-1",
            payload = """{"name":"Milk","quantity":1.0}""",
            createdAt = "2025-01-01T00:00:00Z",
        )

        val mockEngine = MockEngine { _ ->
            throw Exception("failed to connect")
        }

        val listApi = createTestListApi(mockEngine)
        val syncManager = SyncManager(
            database = database,
            listApi = listApi,
            json = testJson,
            onNotification = { notifications.add(it) },
        )

        // Run sync 3 times to exhaust retries
        // Attempt 1: retryCount 0 -> 1
        syncManager.syncPendingChanges()
        assertEquals(1, database.syncQueueQueries.selectAll().executeAsList().size)

        // Attempt 2: retryCount 1 -> 2
        syncManager.syncPendingChanges()
        assertEquals(1, database.syncQueueQueries.selectAll().executeAsList().size)

        // Attempt 3: retryCount 2 -> entry is removed
        syncManager.syncPendingChanges()

        val entries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(0, entries.size, "Entry should be removed after exceeding max retries")

        // Should have received a failure notification
        assertTrue(
            notifications.any { it.contains("Failed to sync") },
            "Should receive a failure notification",
        )
    }
}
