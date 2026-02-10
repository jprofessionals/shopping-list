# Phase 5B: Backend for New UI Features

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add backend support for enhanced UI features: rich list responses, activity tracking, pinned lists, item history/autocomplete, user preferences, and bulk operations.

**Architecture:** New database tables for activity, pinned lists, item history, and preferences. Enhanced list endpoints with summary data. New REST endpoints for all features.

**Tech Stack:** Kotlin/Ktor, Exposed ORM, PostgreSQL with pg_trgm extension for fuzzy search.

---

## Task 1: Add pg_trgm Extension for Fuzzy Search

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt`

**Step 1: Add extension creation on database init**

After schema creation, execute:
```kotlin
exec("CREATE EXTENSION IF NOT EXISTS pg_trgm")
```

**Step 2: Verify by running the app**

Run: `cd backend && ./gradlew run` (with database running)
Expected: No errors, extension created

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt
git commit -m "feat: enable pg_trgm extension for fuzzy search"
```

---

## Task 2: Activity Tracking Domain Model

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/ListActivity.kt`

**Step 1: Create domain model**

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

object ListActivities : UUIDTable("list_activity") {
    val list = reference("list_id", ShoppingLists)
    val account = reference("account_id", Accounts)
    val actionType = varchar("action_type", 50)
    val targetName = varchar("target_name", 255).nullable()
    val createdAt = timestamp("created_at")
}

class ListActivity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListActivity>(ListActivities)

    var list by ShoppingList referencedOn ListActivities.list
    var account by Account referencedOn ListActivities.account
    var actionType by ListActivities.actionType
    var targetName by ListActivities.targetName
    var createdAt by ListActivities.createdAt
}

enum class ActivityType(val value: String) {
    ITEM_ADDED("item_added"),
    ITEM_UPDATED("item_updated"),
    ITEM_CHECKED("item_checked"),
    ITEM_UNCHECKED("item_unchecked"),
    ITEM_REMOVED("item_removed"),
    LIST_CREATED("list_created"),
    LIST_UPDATED("list_updated"),
}
```

**Step 2: Add to schema creation in DatabaseConfig.kt**

Add `ListActivities` to the SchemaUtils.create() call.

**Step 3: Run lint and commit**

```bash
git add -A && git commit -m "feat: add ListActivity domain model"
```

---

## Task 3: Pinned Lists Domain Model

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/PinnedList.kt`

**Step 1: Create domain model**

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PinnedLists : UUIDTable("pinned_list") {
    val account = reference("account_id", Accounts)
    val list = reference("list_id", ShoppingLists)
    val pinnedAt = timestamp("pinned_at")

    init {
        uniqueIndex(account, list)
    }
}

class PinnedList(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PinnedList>(PinnedLists)

    var account by Account referencedOn PinnedLists.account
    var list by ShoppingList referencedOn PinnedLists.list
    var pinnedAt by PinnedLists.pinnedAt
}
```

**Step 2: Add to schema creation**

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add PinnedList domain model"
```

---

## Task 4: Item History Domain Model

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/ItemHistory.kt`

**Step 1: Create domain model**

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object ItemHistories : UUIDTable("item_history") {
    val account = reference("account_id", Accounts)
    val name = varchar("name", 255) // normalized lowercase
    val displayName = varchar("display_name", 255)
    val typicalQuantity = double("typical_quantity").default(1.0)
    val typicalUnit = varchar("typical_unit", 50).nullable()
    val useCount = integer("use_count").default(1)
    val lastUsedAt = timestamp("last_used_at")

    init {
        uniqueIndex(account, name)
    }
}

class ItemHistory(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ItemHistory>(ItemHistories)

    var account by Account referencedOn ItemHistories.account
    var name by ItemHistories.name
    var displayName by ItemHistories.displayName
    var typicalQuantity by ItemHistories.typicalQuantity
    var typicalUnit by ItemHistories.typicalUnit
    var useCount by ItemHistories.useCount
    var lastUsedAt by ItemHistories.lastUsedAt
}
```

**Step 2: Add to schema creation**

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add ItemHistory domain model"
```

---

## Task 5: User Preferences Domain Model

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/UserPreferences.kt`

**Step 1: Create domain model**

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object UserPreferencesTable : UUIDTable("user_preferences") {
    val account = reference("account_id", Accounts).uniqueIndex()
    val smartParsingEnabled = bool("smart_parsing_enabled").default(true)
    val defaultQuantity = double("default_quantity").default(1.0)
    val theme = varchar("theme", 20).default("system")
    val updatedAt = timestamp("updated_at")
}

class UserPreferences(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserPreferences>(UserPreferencesTable)

    var account by Account referencedOn UserPreferencesTable.account
    var smartParsingEnabled by UserPreferencesTable.smartParsingEnabled
    var defaultQuantity by UserPreferencesTable.defaultQuantity
    var theme by UserPreferencesTable.theme
    var updatedAt by UserPreferencesTable.updatedAt
}
```

**Step 2: Add to schema creation**

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add UserPreferences domain model"
```

---

## Task 6: Update DatabaseConfig Schema Creation

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt`

**Step 1: Add all new tables to schema creation**

Update the SchemaUtils.create() call to include:
- ListActivities
- PinnedLists
- ItemHistories
- UserPreferencesTable

**Step 2: Run tests to verify schema works**

Run: `cd backend && ./gradlew test`
Expected: All tests pass

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add new tables to schema creation"
```

---

## Task 7: Activity Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/ActivityService.kt`

**Step 1: Create service**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ActivityType
import no.shoppinglist.domain.ListActivities
import no.shoppinglist.domain.ListActivity
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ActivityService(private val db: Database) {

    fun recordActivity(
        listId: UUID,
        accountId: UUID,
        actionType: ActivityType,
        targetName: String?,
    ) {
        transaction(db) {
            val list = ShoppingList.findById(listId) ?: return@transaction
            val account = Account.findById(accountId) ?: return@transaction

            ListActivity.new {
                this.list = list
                this.account = account
                this.actionType = actionType.value
                this.targetName = targetName
                this.createdAt = Instant.now()
            }

            // Cleanup old activities (keep last 100 or 7 days)
            cleanupOldActivities(listId)
        }
    }

    fun getActivities(listId: UUID, limit: Int = 20): List<ListActivity> {
        return transaction(db) {
            ListActivity.find { ListActivities.list eq listId }
                .orderBy(ListActivities.createdAt to SortOrder.DESC)
                .limit(limit)
                .toList()
        }
    }

    fun getLatestActivity(listId: UUID): ListActivity? {
        return transaction(db) {
            ListActivity.find { ListActivities.list eq listId }
                .orderBy(ListActivities.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }
    }

    private fun cleanupOldActivities(listId: UUID) {
        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)

        // Delete activities older than 7 days
        ListActivities.deleteWhere {
            (ListActivities.list eq listId) and (ListActivities.createdAt less sevenDaysAgo)
        }

        // Keep only last 100
        val activities = ListActivity.find { ListActivities.list eq listId }
            .orderBy(ListActivities.createdAt to SortOrder.DESC)
            .toList()

        if (activities.size > 100) {
            activities.drop(100).forEach { it.delete() }
        }
    }
}
```

**Step 2: Run lint**

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add ActivityService for tracking list activity"
```

---

## Task 8: Pinned List Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/PinnedListService.kt`

**Step 1: Create service**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.PinnedList
import no.shoppinglist.domain.PinnedLists
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class PinnedListService(private val db: Database) {

    fun pin(accountId: UUID, listId: UUID): Boolean {
        return transaction(db) {
            val account = Account.findById(accountId) ?: return@transaction false
            val list = ShoppingList.findById(listId) ?: return@transaction false

            // Check if already pinned
            val existing = PinnedList.find {
                (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
            }.firstOrNull()

            if (existing != null) return@transaction true

            PinnedList.new {
                this.account = account
                this.list = list
                this.pinnedAt = Instant.now()
            }
            true
        }
    }

    fun unpin(accountId: UUID, listId: UUID): Boolean {
        return transaction(db) {
            val deleted = PinnedLists.deleteWhere {
                (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
            }
            deleted > 0
        }
    }

    fun isPinned(accountId: UUID, listId: UUID): Boolean {
        return transaction(db) {
            PinnedList.find {
                (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
            }.firstOrNull() != null
        }
    }

    fun getPinnedListIds(accountId: UUID): Set<UUID> {
        return transaction(db) {
            PinnedList.find { PinnedLists.account eq accountId }
                .map { it.list.id.value }
                .toSet()
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add PinnedListService"
```

---

## Task 9: Item History Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/ItemHistoryService.kt`

**Step 1: Create service with fuzzy search**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ItemHistories
import no.shoppinglist.domain.ItemHistory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ItemHistoryService(private val db: Database) {

    fun recordItemUsage(
        accountId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
    ) {
        transaction(db) {
            val account = Account.findById(accountId) ?: return@transaction
            val normalizedName = name.lowercase().trim()

            val existing = ItemHistory.find {
                (ItemHistories.account eq accountId) and (ItemHistories.name eq normalizedName)
            }.firstOrNull()

            if (existing != null) {
                existing.useCount += 1
                existing.lastUsedAt = Instant.now()
                // Update typical values based on most recent usage
                existing.typicalQuantity = quantity
                if (unit != null) existing.typicalUnit = unit
                existing.displayName = name.trim() // Keep latest casing
            } else {
                ItemHistory.new {
                    this.account = account
                    this.name = normalizedName
                    this.displayName = name.trim()
                    this.typicalQuantity = quantity
                    this.typicalUnit = unit
                    this.useCount = 1
                    this.lastUsedAt = Instant.now()
                }
            }
        }
    }

    fun searchSuggestions(accountId: UUID, query: String, limit: Int = 10): List<ItemHistory> {
        if (query.isBlank()) return emptyList()

        return transaction(db) {
            val normalizedQuery = query.lowercase().trim()

            // Use pg_trgm similarity for fuzzy matching
            // Falls back to LIKE if similarity function not available
            exec(
                """
                SELECT id FROM item_history
                WHERE account_id = '$accountId'
                AND (
                    similarity(name, '$normalizedQuery') > 0.3
                    OR name LIKE '%$normalizedQuery%'
                )
                ORDER BY
                    similarity(name, '$normalizedQuery') * use_count DESC,
                    use_count DESC,
                    last_used_at DESC
                LIMIT $limit
                """.trimIndent()
            ) { rs ->
                val ids = mutableListOf<UUID>()
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("id")))
                }
                ids
            }?.mapNotNull { id ->
                ItemHistory.findById(id)
            } ?: emptyList()
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add ItemHistoryService with fuzzy search"
```

---

## Task 10: User Preferences Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/PreferencesService.kt`

**Step 1: Create service**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.UserPreferences
import no.shoppinglist.domain.UserPreferencesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class PreferencesData(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
)

class PreferencesService(private val db: Database) {

    fun getPreferences(accountId: UUID): PreferencesData {
        return transaction(db) {
            val prefs = UserPreferences.find { UserPreferencesTable.account eq accountId }
                .firstOrNull()

            prefs?.let {
                PreferencesData(
                    smartParsingEnabled = it.smartParsingEnabled,
                    defaultQuantity = it.defaultQuantity,
                    theme = it.theme,
                )
            } ?: PreferencesData(
                smartParsingEnabled = true,
                defaultQuantity = 1.0,
                theme = "system",
            )
        }
    }

    fun updatePreferences(
        accountId: UUID,
        smartParsingEnabled: Boolean?,
        defaultQuantity: Double?,
        theme: String?,
    ): PreferencesData {
        return transaction(db) {
            val account = Account.findById(accountId)
                ?: throw IllegalArgumentException("Account not found")

            val prefs = UserPreferences.find { UserPreferencesTable.account eq accountId }
                .firstOrNull() ?: UserPreferences.new {
                    this.account = account
                    this.smartParsingEnabled = true
                    this.defaultQuantity = 1.0
                    this.theme = "system"
                    this.updatedAt = Instant.now()
                }

            smartParsingEnabled?.let { prefs.smartParsingEnabled = it }
            defaultQuantity?.let { prefs.defaultQuantity = it }
            theme?.let { prefs.theme = it }
            prefs.updatedAt = Instant.now()

            PreferencesData(
                smartParsingEnabled = prefs.smartParsingEnabled,
                defaultQuantity = prefs.defaultQuantity,
                theme = prefs.theme,
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add PreferencesService"
```

---

## Task 11: Enhanced List Response in ShoppingListService

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt`

**Step 1: Add method for enhanced list data**

Add a new data class and method to get lists with summary data:

```kotlin
data class ListSummary(
    val list: ShoppingList,
    val itemCount: Int,
    val uncheckedCount: Int,
    val previewItems: List<String>,
    val isPinned: Boolean,
)

fun findAccessibleByAccountWithSummary(
    accountId: UUID,
    pinnedListIds: Set<UUID>,
): List<ListSummary> {
    return transaction(db) {
        val lists = findAccessibleByAccount(accountId)
        lists.map { list ->
            val items = ListItem.find { ListItems.list eq list.id }.toList()
            ListSummary(
                list = list,
                itemCount = items.size,
                uncheckedCount = items.count { !it.isChecked },
                previewItems = items.filter { !it.isChecked }.take(3).map { it.name },
                isPinned = pinnedListIds.contains(list.id.value),
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add enhanced list summary to ShoppingListService"
```

---

## Task 12: Activity Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/ActivityRoutes.kt`

**Step 1: Create routes**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.ShoppingListService
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class ActivityResponse(
    val type: String,
    val actorName: String,
    val targetName: String?,
    val timestamp: String,
)

fun Route.activityRoutes(
    activityService: ActivityService,
    shoppingListService: ShoppingListService,
) {
    authenticate("auth-jwt") {
        route("/lists/{id}/activity") {
            get {
                val accountId = call.principal<JWTPrincipal>()
                    ?.subject?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val listId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                // Verify access
                val permission = shoppingListService.getPermission(listId, accountId, null)
                if (permission == null) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val activities = activityService.getActivities(listId, limit)
                val response = activities.map { activity ->
                    transaction {
                        ActivityResponse(
                            type = activity.actionType,
                            actorName = activity.account.displayName,
                            targetName = activity.targetName,
                            timestamp = activity.createdAt.toString(),
                        )
                    }
                }

                call.respond(mapOf("activity" to response))
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add activity routes"
```

---

## Task 13: Pin Routes

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`

**Step 1: Add pin/unpin endpoints to existing routes**

Inside the `route("/{id}")` block, add:

```kotlin
post("/pin") {
    val accountId = call.principal<JWTPrincipal>()
        ?.subject?.let { UUID.fromString(it) }
        ?: return@post call.respond(HttpStatusCode.Unauthorized)

    val listId = call.parameters["id"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return@post call.respond(HttpStatusCode.BadRequest)

    // Verify access
    val permission = shoppingListService.getPermission(listId, accountId, null)
    if (permission == null) {
        return@post call.respond(HttpStatusCode.Forbidden)
    }

    pinnedListService.pin(accountId, listId)
    call.respond(HttpStatusCode.OK, mapOf("pinned" to true))
}

delete("/pin") {
    val accountId = call.principal<JWTPrincipal>()
        ?.subject?.let { UUID.fromString(it) }
        ?: return@delete call.respond(HttpStatusCode.Unauthorized)

    val listId = call.parameters["id"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return@delete call.respond(HttpStatusCode.BadRequest)

    pinnedListService.unpin(accountId, listId)
    call.respond(HttpStatusCode.OK, mapOf("pinned" to false))
}
```

Note: Will need to add `pinnedListService` parameter to function signature.

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add pin/unpin endpoints"
```

---

## Task 14: Item Suggestions Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/SuggestionRoutes.kt`

**Step 1: Create routes**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.ItemHistoryService
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class SuggestionResponse(
    val name: String,
    val typicalQuantity: Double,
    val typicalUnit: String?,
    val useCount: Int,
)

fun Route.suggestionRoutes(itemHistoryService: ItemHistoryService) {
    authenticate("auth-jwt") {
        route("/items/suggestions") {
            get {
                val accountId = call.principal<JWTPrincipal>()
                    ?.subject?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val query = call.request.queryParameters["q"] ?: ""
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

                val suggestions = itemHistoryService.searchSuggestions(accountId, query, limit)
                val response = suggestions.map { item ->
                    transaction {
                        SuggestionResponse(
                            name = item.displayName,
                            typicalQuantity = item.typicalQuantity,
                            typicalUnit = item.typicalUnit,
                            useCount = item.useCount,
                        )
                    }
                }

                call.respond(mapOf("suggestions" to response))
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add item suggestions endpoint"
```

---

## Task 15: Preferences Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/PreferencesRoutes.kt`

**Step 1: Create routes**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.PreferencesService
import java.util.UUID

@Serializable
data class PreferencesResponse(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
)

@Serializable
data class UpdatePreferencesRequest(
    val smartParsingEnabled: Boolean? = null,
    val defaultQuantity: Double? = null,
    val theme: String? = null,
)

fun Route.preferencesRoutes(preferencesService: PreferencesService) {
    authenticate("auth-jwt") {
        route("/preferences") {
            get {
                val accountId = call.principal<JWTPrincipal>()
                    ?.subject?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val prefs = preferencesService.getPreferences(accountId)
                call.respond(
                    PreferencesResponse(
                        smartParsingEnabled = prefs.smartParsingEnabled,
                        defaultQuantity = prefs.defaultQuantity,
                        theme = prefs.theme,
                    )
                )
            }

            patch {
                val accountId = call.principal<JWTPrincipal>()
                    ?.subject?.let { UUID.fromString(it) }
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<UpdatePreferencesRequest>()
                val prefs = preferencesService.updatePreferences(
                    accountId = accountId,
                    smartParsingEnabled = request.smartParsingEnabled,
                    defaultQuantity = request.defaultQuantity,
                    theme = request.theme,
                )

                call.respond(
                    PreferencesResponse(
                        smartParsingEnabled = prefs.smartParsingEnabled,
                        defaultQuantity = prefs.defaultQuantity,
                        theme = prefs.theme,
                    )
                )
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add preferences endpoints"
```

---

## Task 16: Bulk Operations in ShoppingListRoutes

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`

**Step 1: Add bulk delete and bulk create endpoints**

Inside the items route, add:

```kotlin
// DELETE /lists/:id/items/checked - Clear all checked items
delete("/checked") {
    val accountId = call.principal<JWTPrincipal>()
        ?.subject?.let { UUID.fromString(it) }
        ?: return@delete call.respond(HttpStatusCode.Unauthorized)

    val listId = call.parameters["id"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return@delete call.respond(HttpStatusCode.BadRequest)

    // Check WRITE permission
    val permission = shoppingListService.getPermission(listId, accountId, null)
    if (permission != SharePermission.WRITE) {
        return@delete call.respond(HttpStatusCode.Forbidden)
    }

    val deletedIds = listItemService.deleteCheckedItems(listId)
    call.respond(mapOf("deletedItemIds" to deletedIds.map { it.toString() }))
}

// POST /lists/:id/items/bulk - Bulk create items
post("/bulk") {
    val accountId = call.principal<JWTPrincipal>()
        ?.subject?.let { UUID.fromString(it) }
        ?: return@post call.respond(HttpStatusCode.Unauthorized)

    val listId = call.parameters["id"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return@post call.respond(HttpStatusCode.BadRequest)

    // Check WRITE permission
    val permission = shoppingListService.getPermission(listId, accountId, null)
    if (permission != SharePermission.WRITE) {
        return@post call.respond(HttpStatusCode.Forbidden)
    }

    val request = call.receive<BulkCreateItemsRequest>()
    val items = request.items.map { itemReq ->
        listItemService.create(
            listId = listId,
            name = itemReq.name,
            quantity = itemReq.quantity,
            unit = itemReq.unit,
            barcode = null,
            createdById = accountId,
        )
    }

    call.respond(
        HttpStatusCode.Created,
        items.map { item ->
            transaction {
                ItemResponse(
                    id = item.id.value.toString(),
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    isChecked = item.isChecked,
                    checkedByName = item.checkedBy?.displayName,
                    createdAt = item.createdAt.toString(),
                )
            }
        }
    )
}
```

Add request class:
```kotlin
@Serializable
data class BulkCreateItemsRequest(
    val items: List<CreateItemRequest>,
)
```

**Step 2: Add deleteCheckedItems to ListItemService**

```kotlin
fun deleteCheckedItems(listId: UUID): List<UUID> {
    return transaction(db) {
        val checkedItems = ListItem.find {
            (ListItems.list eq listId) and (ListItems.isChecked eq true)
        }.toList()

        val deletedIds = checkedItems.map { it.id.value }
        checkedItems.forEach { it.delete() }
        deletedIds
    }
}
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add bulk operations for items"
```

---

## Task 17: Wire Up New Services in Application.kt

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Instantiate new services and add routes**

Add service instantiation after existing services:
```kotlin
val activityService = ActivityService(db)
val pinnedListService = PinnedListService(db)
val itemHistoryService = ItemHistoryService(db)
val preferencesService = PreferencesService(db)
```

Add new routes in configureRouting:
```kotlin
activityRoutes(activityService, shoppingListService)
suggestionRoutes(itemHistoryService)
preferencesRoutes(preferencesService)
```

Update shoppingListRoutes call to include pinnedListService and itemHistoryService.

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: wire up new services and routes"
```

---

## Task 18: Record Activity on Item Operations

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`

**Step 1: Add activity recording after item mutations**

After item add:
```kotlin
activityService.recordActivity(listId, accountId, ActivityType.ITEM_ADDED, item.name)
```

After item update:
```kotlin
activityService.recordActivity(listId, accountId, ActivityType.ITEM_UPDATED, updated.name)
```

After item check/uncheck:
```kotlin
val activityType = if (toggled.isChecked) ActivityType.ITEM_CHECKED else ActivityType.ITEM_UNCHECKED
activityService.recordActivity(listId, accountId, activityType, toggled.name)
```

After item delete:
```kotlin
// Need to get item name before delete
activityService.recordActivity(listId, accountId, ActivityType.ITEM_REMOVED, itemName)
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: record activity on item operations"
```

---

## Task 19: Record Item History on Add

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`

**Step 1: Record item usage for autocomplete**

After successful item creation:
```kotlin
itemHistoryService.recordItemUsage(accountId, item.name, item.quantity, item.unit)
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: record item history for autocomplete"
```

---

## Task 20: Update OpenAPI Documentation

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Step 1: Add all new endpoints**

Add documentation for:
- GET /lists/{id}/activity
- POST /lists/{id}/pin
- DELETE /lists/{id}/pin
- GET /items/suggestions
- GET /preferences
- PATCH /preferences
- DELETE /lists/{id}/items/checked
- POST /lists/{id}/items/bulk

**Step 2: Commit**

```bash
git add -A && git commit -m "docs: add new endpoints to OpenAPI spec"
```

---

## Task 21: Run Full Test Suite

**Files:** None (verification only)

**Step 1: Run all tests**

Run: `make test`
Expected: All tests pass

**Step 2: Run lint**

Run: `make lint`
Expected: No errors

**Step 3: Fix any issues and commit**

---

## Summary

After completing all tasks, Phase 5B delivers:
- Activity tracking with GET /lists/:id/activity endpoint
- Pinned lists with POST/DELETE /lists/:id/pin endpoints
- Item history and fuzzy search autocomplete with GET /items/suggestions
- User preferences with GET/PATCH /preferences
- Bulk operations: DELETE /lists/:id/items/checked and POST /lists/:id/items/bulk
- Activity recording on all item operations
- Item history recording for autocomplete suggestions
- Updated OpenAPI documentation

The frontend can now use these endpoints for the enhanced UI features planned in Phase 5D.
