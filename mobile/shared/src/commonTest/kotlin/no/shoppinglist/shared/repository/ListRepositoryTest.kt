package no.shoppinglist.shared.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import no.shoppinglist.shared.api.routes.ListApi
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.test.createTestDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListRepositoryTest {

    private lateinit var database: ShoppingListDatabase
    private val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
    }

    private fun createListApiWithMockEngine(mockEngine: MockEngine): ListApi {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            defaultRequest { url("http://localhost:8080") }
        }
        return TestListApi(httpClient, testJson)
    }

    @Test
    fun addItemWithApiSuccessUpdatesLocalCache() = runTest {
        val itemResponse = ItemResponse(
            id = "item-1",
            name = "Milk",
            quantity = 2.0,
            unit = "liters",
            isChecked = false,
            checkedByName = null,
            createdAt = "2025-01-01T00:00:00Z",
        )

        val mockEngine = MockEngine { _ ->
            respond(
                content = testJson.encodeToString(ItemResponse.serializer(), itemResponse),
                status = HttpStatusCode.Created,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val listApi = createListApiWithMockEngine(mockEngine)
        val repository = ListRepository(listApi, database, testJson)

        repository.addItem(
            listId = "list-1",
            name = "Milk",
            quantity = 2.0,
            unit = "liters",
        )

        val items = database.listItemQueries.selectByListId("list-1").executeAsList()
        assertEquals(1, items.size)
        assertEquals("item-1", items[0].id)
        assertEquals("Milk", items[0].name)
        assertEquals(2.0, items[0].quantity)
        assertEquals("liters", items[0].unit)

        // No sync queue entries should exist
        val syncEntries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(0, syncEntries.size)
    }

    @Test
    fun addItemWithNetworkErrorAddsToSyncQueueAndLocalCache() = runTest {
        val mockEngine = MockEngine { _ ->
            throw Exception("failed to connect")
        }

        val listApi = createListApiWithMockEngine(mockEngine)
        val repository = ListRepository(listApi, database, testJson)

        repository.addItem(
            listId = "list-1",
            name = "Bread",
            quantity = 1.0,
            unit = null,
        )

        // Item should be in local cache with a temp ID
        val items = database.listItemQueries.selectByListId("list-1").executeAsList()
        assertEquals(1, items.size)
        assertEquals("Bread", items[0].name)
        assertTrue(items[0].id.startsWith("temp_"))

        // Sync queue entry should exist
        val syncEntries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(1, syncEntries.size)
        assertEquals("CREATE", syncEntries[0].operationType)
        assertEquals("ITEM", syncEntries[0].entityType)
        assertEquals("list-1", syncEntries[0].parentId)
    }

    @Test
    fun clearCheckedRemovesCheckedItemsLocallyWhenOffline() = runTest {
        // Insert items: 2 checked, 1 unchecked
        database.listItemQueries.insert(
            id = "item-1",
            listId = "list-1",
            name = "Milk",
            quantity = 1.0,
            unit = null,
            isChecked = true,
            checkedByName = "User",
            createdAt = "2025-01-01T00:00:00Z",
        )
        database.listItemQueries.insert(
            id = "item-2",
            listId = "list-1",
            name = "Bread",
            quantity = 1.0,
            unit = null,
            isChecked = true,
            checkedByName = "User",
            createdAt = "2025-01-01T00:01:00Z",
        )
        database.listItemQueries.insert(
            id = "item-3",
            listId = "list-1",
            name = "Eggs",
            quantity = 12.0,
            unit = "pcs",
            isChecked = false,
            checkedByName = null,
            createdAt = "2025-01-01T00:02:00Z",
        )

        val mockEngine = MockEngine { _ ->
            throw Exception("failed to connect")
        }

        val listApi = createListApiWithMockEngine(mockEngine)
        val repository = ListRepository(listApi, database, testJson)

        repository.clearChecked("list-1")

        // Only the unchecked item should remain
        val remainingItems = database.listItemQueries.selectByListId("list-1").executeAsList()
        assertEquals(1, remainingItems.size)
        assertEquals("Eggs", remainingItems[0].name)
        assertEquals(false, remainingItems[0].isChecked)

        // Sync queue entry should be created
        val syncEntries = database.syncQueueQueries.selectAll().executeAsList()
        assertEquals(1, syncEntries.size)
        assertEquals("CLEAR_CHECKED", syncEntries[0].operationType)
        assertEquals("LIST", syncEntries[0].entityType)
        assertEquals("list-1", syncEntries[0].entityId)
    }
}
