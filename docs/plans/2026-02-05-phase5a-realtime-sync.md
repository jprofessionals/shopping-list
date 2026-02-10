# Phase 5A: Real-time Sync Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add WebSocket infrastructure for real-time updates when list items change.

**Architecture:** Ktor WebSocket endpoint at `/ws` with JWT authentication. Clients subscribe to lists they can access. Services broadcast events through a central `EventBroadcaster`. Events include item CRUD and list changes.

**Tech Stack:** Ktor WebSockets (`ktor-server-websockets-jvm` already in build.gradle.kts), kotlinx.serialization for event payloads, ConcurrentHashMap for session management.

---

## Task 1: WebSocket Event Types

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketEvent.kt`

**Step 1: Create event sealed class hierarchy**

```kotlin
package no.shoppinglist.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
sealed class WebSocketEvent {
    abstract val type: String
    abstract val timestamp: String
}

@Serializable
data class ActorInfo(
    val id: String,
    val displayName: String,
)

// Item events
@Serializable
@SerialName("item:added")
data class ItemAddedEvent(
    override val type: String = "item:added",
    val listId: String,
    val item: ItemData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:updated")
data class ItemUpdatedEvent(
    override val type: String = "item:updated",
    val listId: String,
    val item: ItemData,
    val changes: List<String>,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:checked")
data class ItemCheckedEvent(
    override val type: String = "item:checked",
    val listId: String,
    val itemId: String,
    val isChecked: Boolean,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:removed")
data class ItemRemovedEvent(
    override val type: String = "item:removed",
    val listId: String,
    val itemId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// List events
@Serializable
@SerialName("list:created")
data class ListCreatedEvent(
    override val type: String = "list:created",
    val list: ListData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("list:updated")
data class ListUpdatedEvent(
    override val type: String = "list:updated",
    val list: ListData,
    val changes: List<String>,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("list:deleted")
data class ListDeletedEvent(
    override val type: String = "list:deleted",
    val listId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// Data transfer objects for events
@Serializable
data class ItemData(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val checkedByName: String?,
)

@Serializable
data class ListData(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
)
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketEvent.kt
git commit -m "feat(websocket): add event types for real-time sync"
```

---

## Task 2: WebSocket Session Manager

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketSessionManager.kt`

**Step 1: Create session manager**

```kotlin
package no.shoppinglist.websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSocketSessionManager {
    // accountId -> list of sessions (one user can have multiple tabs/devices)
    private val sessions = ConcurrentHashMap<UUID, MutableList<WebSocketSession>>()

    // listId -> set of accountIds subscribed to that list
    private val listSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // accountId -> set of listIds they're subscribed to
    private val accountSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private val json = Json { encodeDefaults = true }

    fun addSession(accountId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(accountId) { mutableListOf() }.add(session)
    }

    fun removeSession(accountId: UUID, session: WebSocketSession) {
        sessions[accountId]?.remove(session)
        if (sessions[accountId]?.isEmpty() == true) {
            sessions.remove(accountId)
            // Clean up subscriptions when user has no more sessions
            accountSubscriptions[accountId]?.forEach { listId ->
                listSubscriptions[listId]?.remove(accountId)
            }
            accountSubscriptions.remove(accountId)
        }
    }

    fun subscribeToList(accountId: UUID, listId: UUID) {
        listSubscriptions.computeIfAbsent(listId) { mutableSetOf() }.add(accountId)
        accountSubscriptions.computeIfAbsent(accountId) { mutableSetOf() }.add(listId)
    }

    fun unsubscribeFromList(accountId: UUID, listId: UUID) {
        listSubscriptions[listId]?.remove(accountId)
        accountSubscriptions[accountId]?.remove(listId)
    }

    fun getSubscribedLists(accountId: UUID): Set<UUID> {
        return accountSubscriptions[accountId]?.toSet() ?: emptySet()
    }

    suspend fun broadcastToList(listId: UUID, event: WebSocketEvent, excludeAccountId: UUID? = null) {
        val subscribers = listSubscriptions[listId] ?: return
        val message = json.encodeToString(event)

        subscribers
            .filter { it != excludeAccountId }
            .flatMap { accountId -> sessions[accountId] ?: emptyList() }
            .forEach { session ->
                try {
                    session.send(message)
                } catch (_: Exception) {
                    // Session might be closed, will be cleaned up
                }
            }
    }

    suspend fun sendToAccount(accountId: UUID, event: WebSocketEvent) {
        val message = json.encodeToString(event)
        sessions[accountId]?.forEach { session ->
            try {
                session.send(message)
            } catch (_: Exception) {
                // Session might be closed
            }
        }
    }

    fun getConnectedAccountCount(): Int = sessions.size

    fun getSubscriptionCount(listId: UUID): Int = listSubscriptions[listId]?.size ?: 0
}
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketSessionManager.kt
git commit -m "feat(websocket): add session manager for tracking connections"
```

---

## Task 3: Event Broadcaster Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/websocket/EventBroadcaster.kt`

**Step 1: Create broadcaster that services will call**

```kotlin
package no.shoppinglist.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class EventBroadcaster(
    private val sessionManager: WebSocketSessionManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun broadcastItemAdded(item: ListItem, actorId: UUID) {
        scope.launch {
            val (listId, event) = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                val listId = item.list.id.value
                listId to ItemAddedEvent(
                    listId = listId.toString(),
                    item = item.toItemData(),
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    fun broadcastItemUpdated(item: ListItem, changes: List<String>, actorId: UUID) {
        scope.launch {
            val (listId, event) = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                val listId = item.list.id.value
                listId to ItemUpdatedEvent(
                    listId = listId.toString(),
                    item = item.toItemData(),
                    changes = changes,
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    fun broadcastItemChecked(item: ListItem, actorId: UUID) {
        scope.launch {
            val (listId, event) = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                val listId = item.list.id.value
                listId to ItemCheckedEvent(
                    listId = listId.toString(),
                    itemId = item.id.value.toString(),
                    isChecked = item.isChecked,
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    fun broadcastItemRemoved(listId: UUID, itemId: UUID, actorId: UUID) {
        scope.launch {
            val event = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                ItemRemovedEvent(
                    listId = listId.toString(),
                    itemId = itemId.toString(),
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    fun broadcastListCreated(list: ShoppingList, actorId: UUID) {
        scope.launch {
            val event = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                ListCreatedEvent(
                    list = list.toListData(),
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            // Broadcast to all household members if household list
            val householdId = transaction { list.household?.id?.value }
            if (householdId != null) {
                // TODO: Get all household member IDs and send to them
                // For now, this will be enhanced when we add household subscriptions
            }
        }
    }

    fun broadcastListUpdated(list: ShoppingList, changes: List<String>, actorId: UUID) {
        scope.launch {
            val (listId, event) = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                list.id.value to ListUpdatedEvent(
                    list = list.toListData(),
                    changes = changes,
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    fun broadcastListDeleted(listId: UUID, actorId: UUID) {
        scope.launch {
            val event = transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                ListDeletedEvent(
                    listId = listId.toString(),
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

            sessionManager.broadcastToList(listId, event, excludeAccountId = actorId)
        }
    }

    private fun ListItem.toItemData(): ItemData = ItemData(
        id = id.value.toString(),
        name = name,
        quantity = quantity,
        unit = unit,
        isChecked = isChecked,
        checkedByName = checkedBy?.displayName,
    )

    private fun ShoppingList.toListData(): ListData = ListData(
        id = id.value.toString(),
        name = name,
        householdId = household?.id?.value?.toString(),
        isPersonal = isPersonal,
    )

    private fun Account.toActorInfo(): ActorInfo = ActorInfo(
        id = id.value.toString(),
        displayName = displayName,
    )
}
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/websocket/EventBroadcaster.kt
git commit -m "feat(websocket): add event broadcaster for services to emit events"
```

---

## Task 4: WebSocket Client Commands

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketCommand.kt`

**Step 1: Create command types for client-to-server messages**

```kotlin
package no.shoppinglist.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class WebSocketCommand {
    abstract val type: String
}

@Serializable
@SerialName("subscribe")
data class SubscribeCommand(
    override val type: String = "subscribe",
    val listIds: List<String>,
) : WebSocketCommand()

@Serializable
@SerialName("unsubscribe")
data class UnsubscribeCommand(
    override val type: String = "unsubscribe",
    val listIds: List<String>,
) : WebSocketCommand()

@Serializable
@SerialName("ping")
data class PingCommand(
    override val type: String = "ping",
) : WebSocketCommand()

// Response events sent to client
@Serializable
data class SubscribedEvent(
    val type: String = "subscribed",
    val listIds: List<String>,
)

@Serializable
data class UnsubscribedEvent(
    val type: String = "unsubscribed",
    val listIds: List<String>,
)

@Serializable
data class PongEvent(
    val type: String = "pong",
)

@Serializable
data class ErrorEvent(
    val type: String = "error",
    val message: String,
    val code: String,
)
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketCommand.kt
git commit -m "feat(websocket): add client command types"
```

---

## Task 5: WebSocket Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/WebSocketRoutes.kt`

**Step 1: Create WebSocket endpoint with JWT authentication**

```kotlin
package no.shoppinglist.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import no.shoppinglist.config.JwtConfig
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.websocket.ErrorEvent
import no.shoppinglist.websocket.PingCommand
import no.shoppinglist.websocket.PongEvent
import no.shoppinglist.websocket.SubscribeCommand
import no.shoppinglist.websocket.SubscribedEvent
import no.shoppinglist.websocket.UnsubscribeCommand
import no.shoppinglist.websocket.UnsubscribedEvent
import no.shoppinglist.websocket.WebSocketCommand
import no.shoppinglist.websocket.WebSocketSessionManager
import java.util.UUID

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.webSocketRoutes(
    jwtConfig: JwtConfig,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
) {
    webSocket("/ws") {
        // Get token from query parameter
        val token = call.request.queryParameters["token"]
        if (token == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
            return@webSocket
        }

        // Validate JWT
        val accountId = try {
            val verifier = JWT
                .require(Algorithm.HMAC256(jwtConfig.secret))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (_: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        // Register session
        sessionManager.addSession(accountId, this)

        // Auto-subscribe to all accessible lists
        val accessibleLists = shoppingListService.findAccessibleByAccount(accountId)
        accessibleLists.forEach { list ->
            sessionManager.subscribeToList(accountId, list.id.value)
        }

        // Send initial subscription confirmation
        val listIds = accessibleLists.map { it.id.value.toString() }
        send(Frame.Text(json.encodeToString(SubscribedEvent.serializer(), SubscribedEvent(listIds = listIds))))

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleCommand(text, accountId, sessionManager, shoppingListService)
                }
            }
        } finally {
            sessionManager.removeSession(accountId, this)
        }
    }
}

private suspend fun io.ktor.websocket.WebSocketSession.handleCommand(
    text: String,
    accountId: UUID,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
) {
    try {
        // Parse the type first to determine which command
        val typeMap = json.decodeFromString<Map<String, String>>(text)
        val type = typeMap["type"] ?: return

        when (type) {
            "subscribe" -> {
                val command = json.decodeFromString<SubscribeCommand>(text)
                val validListIds = command.listIds.mapNotNull { listIdStr ->
                    val listId = runCatching { UUID.fromString(listIdStr) }.getOrNull() ?: return@mapNotNull null
                    // Verify access
                    val permission = shoppingListService.getPermission(listId, accountId, null)
                    if (permission != null) {
                        sessionManager.subscribeToList(accountId, listId)
                        listIdStr
                    } else {
                        null
                    }
                }
                send(Frame.Text(json.encodeToString(SubscribedEvent.serializer(), SubscribedEvent(listIds = validListIds))))
            }

            "unsubscribe" -> {
                val command = json.decodeFromString<UnsubscribeCommand>(text)
                command.listIds.forEach { listIdStr ->
                    val listId = runCatching { UUID.fromString(listIdStr) }.getOrNull() ?: return@forEach
                    sessionManager.unsubscribeFromList(accountId, listId)
                }
                send(Frame.Text(json.encodeToString(UnsubscribedEvent.serializer(), UnsubscribedEvent(listIds = command.listIds))))
            }

            "ping" -> {
                send(Frame.Text(json.encodeToString(PongEvent.serializer(), PongEvent())))
            }

            else -> {
                send(Frame.Text(json.encodeToString(ErrorEvent.serializer(), ErrorEvent(
                    message = "Unknown command type: $type",
                    code = "UNKNOWN_COMMAND",
                ))))
            }
        }
    } catch (e: Exception) {
        send(Frame.Text(json.encodeToString(ErrorEvent.serializer(), ErrorEvent(
            message = "Failed to parse command: ${e.message}",
            code = "PARSE_ERROR",
        ))))
    }
}
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/WebSocketRoutes.kt
git commit -m "feat(websocket): add WebSocket endpoint with JWT auth"
```

---

## Task 6: Install WebSockets in Application

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Add WebSocket installation and routing**

Add import at top:
```kotlin
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import no.shoppinglist.routes.webSocketRoutes
import no.shoppinglist.websocket.EventBroadcaster
import no.shoppinglist.websocket.WebSocketSessionManager
import kotlin.time.Duration.Companion.seconds
```

In `module()` function, after `configureAuthentication(authConfig)`, add:
```kotlin
    configureWebSockets()
```

After service instantiation, add:
```kotlin
    val sessionManager = WebSocketSessionManager()
    val eventBroadcaster = EventBroadcaster(sessionManager)
```

Update `configureRouting` call to include new parameters:
```kotlin
    configureRouting(
        authConfig,
        accountService,
        householdService,
        jwtService,
        shoppingListService,
        listItemService,
        listShareService,
        sessionManager,
        eventBroadcaster,
    )
```

Add new function before `configureRouting`:
```kotlin
private fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
```

Update `configureRouting` signature and add websocket route:
```kotlin
private fun Application.configureRouting(
    authConfig: AuthConfig,
    accountService: AccountService,
    householdService: HouseholdService,
    jwtService: JwtService,
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    listShareService: ListShareService,
    sessionManager: WebSocketSessionManager,
    eventBroadcaster: EventBroadcaster,
) {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        get("/health") {
            call.respondText("OK")
        }

        webSocketRoutes(authConfig.jwt, sessionManager, shoppingListService)
        authRoutes(authConfig, accountService, jwtService)
        householdRoutes(householdService, accountService)
        shoppingListRoutes(shoppingListService, listItemService, householdService, listShareService, eventBroadcaster)
        sharedAccessRoutes(listShareService, listItemService)
    }
}
```

**Step 2: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/Application.kt
git commit -m "feat(websocket): integrate WebSocket into application"
```

---

## Task 7: Add Event Broadcasting to Routes

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`

**Step 1: Update function signature to accept EventBroadcaster**

Change the function signature:
```kotlin
fun Route.shoppingListRoutes(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    householdService: HouseholdService,
    listShareService: ListShareService,
    eventBroadcaster: EventBroadcaster,
)
```

Add import:
```kotlin
import no.shoppinglist.websocket.EventBroadcaster
```

**Step 2: Add broadcast calls after successful mutations**

After successful `POST /lists/:id/items` (item creation), add before `call.respond`:
```kotlin
                        eventBroadcaster.broadcastItemAdded(item, accountId)
```

After successful `PATCH /lists/:id/items/:itemId` (item update), add before `call.respond`:
```kotlin
                            eventBroadcaster.broadcastItemUpdated(updated, listOf("name", "quantity", "unit"), accountId)
```

After successful `DELETE /lists/:id/items/:itemId` (item deletion), add before `call.respond`:
```kotlin
                            eventBroadcaster.broadcastItemRemoved(listId, itemId, accountId)
```

After successful `POST /lists/:id/items/:itemId/check` (toggle check), add before `call.respond`:
```kotlin
                            eventBroadcaster.broadcastItemChecked(toggled, accountId)
```

**Step 3: Run lint to verify**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew ktlintCheck --quiet`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt
git commit -m "feat(websocket): broadcast item events from routes"
```

---

## Task 8: WebSocket Integration Test

**Files:**
- Create: `backend/src/test/kotlin/no/shoppinglist/websocket/WebSocketSessionManagerTest.kt`

**Step 1: Write unit tests for session manager**

```kotlin
package no.shoppinglist.websocket

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.websocket.WebSocketSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID

class WebSocketSessionManagerTest : FunSpec({
    lateinit var manager: WebSocketSessionManager

    beforeTest {
        manager = WebSocketSessionManager()
    }

    test("addSession registers account session") {
        val accountId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        manager.addSession(accountId, session)

        manager.getConnectedAccountCount() shouldBe 1
    }

    test("removeSession removes session and cleans up subscriptions") {
        val accountId = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        manager.addSession(accountId, session)
        manager.subscribeToList(accountId, listId)
        manager.removeSession(accountId, session)

        manager.getConnectedAccountCount() shouldBe 0
        manager.getSubscriptionCount(listId) shouldBe 0
    }

    test("subscribeToList adds list to account subscriptions") {
        val accountId = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        manager.addSession(accountId, session)
        manager.subscribeToList(accountId, listId)

        manager.getSubscribedLists(accountId) shouldContain listId
        manager.getSubscriptionCount(listId) shouldBe 1
    }

    test("unsubscribeFromList removes list from subscriptions") {
        val accountId = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        manager.addSession(accountId, session)
        manager.subscribeToList(accountId, listId)
        manager.unsubscribeFromList(accountId, listId)

        manager.getSubscribedLists(accountId).contains(listId) shouldBe false
        manager.getSubscriptionCount(listId) shouldBe 0
    }

    test("broadcastToList sends to all subscribers except excluded") {
        val account1 = UUID.randomUUID()
        val account2 = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()
        val sentMessages = slot<String>()

        coEvery { session1.send(any<io.ktor.websocket.Frame>()) } returns Unit
        coEvery { session2.send(any<io.ktor.websocket.Frame>()) } returns Unit

        manager.addSession(account1, session1)
        manager.addSession(account2, session2)
        manager.subscribeToList(account1, listId)
        manager.subscribeToList(account2, listId)

        val event = ItemCheckedEvent(
            listId = listId.toString(),
            itemId = UUID.randomUUID().toString(),
            isChecked = true,
            actor = ActorInfo(account1.toString(), "User 1"),
        )

        kotlinx.coroutines.runBlocking {
            manager.broadcastToList(listId, event, excludeAccountId = account1)
        }

        // session2 should receive, session1 should not (excluded)
        coVerify(exactly = 1) { session2.send(any<io.ktor.websocket.Frame>()) }
        coVerify(exactly = 0) { session1.send(any<io.ktor.websocket.Frame>()) }
    }
})
```

**Step 2: Run test to verify it passes**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew test --tests "*WebSocketSessionManagerTest*" --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/no/shoppinglist/websocket/WebSocketSessionManagerTest.kt
git commit -m "test(websocket): add unit tests for session manager"
```

---

## Task 9: Add MockK Test Dependency

**Files:**
- Modify: `backend/build.gradle.kts`

**Step 1: Check if mockk is already present, if not add it**

Add to dependencies block:
```kotlin
    testImplementation("io.mockk:mockk:1.13.13")
```

**Step 2: Sync dependencies**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime/backend && ./gradlew dependencies --quiet 2>&1 | tail -3`
Expected: Shows dependency tree

**Step 3: Commit**

```bash
git add backend/build.gradle.kts
git commit -m "chore: add mockk test dependency"
```

---

## Task 10: Run Full Test Suite

**Files:** None (verification only)

**Step 1: Run all backend tests**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime && make test-backend`
Expected: All tests pass

**Step 2: Run lint**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime && make lint-backend`
Expected: No lint errors

**Step 3: Commit any fixes if needed**

---

## Task 11: Update OpenAPI Documentation

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Step 1: Add WebSocket endpoint documentation**

Add to paths section:
```yaml
  /ws:
    get:
      summary: WebSocket connection for real-time updates
      description: |
        Connect via WebSocket to receive real-time updates for shopping lists.

        **Authentication:** Pass JWT token as query parameter `?token=<jwt>`

        **Auto-subscription:** On connect, client is automatically subscribed to all accessible lists.

        **Events received:**
        - `item:added` - New item added to a subscribed list
        - `item:updated` - Item properties changed
        - `item:checked` - Item checked/unchecked
        - `item:removed` - Item deleted
        - `list:updated` - List properties changed
        - `list:deleted` - List deleted

        **Commands to send:**
        - `{"type": "subscribe", "listIds": ["uuid1", "uuid2"]}` - Subscribe to additional lists
        - `{"type": "unsubscribe", "listIds": ["uuid1"]}` - Unsubscribe from lists
        - `{"type": "ping"}` - Keepalive ping (receives pong)

        See AsyncAPI documentation for full event schemas.
      tags:
        - WebSocket
      parameters:
        - name: token
          in: query
          required: true
          schema:
            type: string
          description: JWT authentication token
      responses:
        '101':
          description: WebSocket connection established
        '401':
          description: Invalid or missing token
```

**Step 2: Commit**

```bash
git add backend/src/main/resources/openapi/documentation.yaml
git commit -m "docs: add WebSocket endpoint to OpenAPI spec"
```

---

## Task 12: Create AsyncAPI Documentation

**Files:**
- Create: `backend/src/main/resources/asyncapi/websocket.yaml`

**Step 1: Create AsyncAPI spec for WebSocket events**

```yaml
asyncapi: 2.6.0
info:
  title: Shopping List WebSocket API
  version: 1.0.0
  description: Real-time events for shopping list updates

servers:
  development:
    url: ws://localhost:8080/ws
    protocol: ws
    description: Development WebSocket server

channels:
  /:
    publish:
      summary: Commands sent by client
      message:
        oneOf:
          - $ref: '#/components/messages/SubscribeCommand'
          - $ref: '#/components/messages/UnsubscribeCommand'
          - $ref: '#/components/messages/PingCommand'
    subscribe:
      summary: Events received by client
      message:
        oneOf:
          - $ref: '#/components/messages/ItemAddedEvent'
          - $ref: '#/components/messages/ItemUpdatedEvent'
          - $ref: '#/components/messages/ItemCheckedEvent'
          - $ref: '#/components/messages/ItemRemovedEvent'
          - $ref: '#/components/messages/ListUpdatedEvent'
          - $ref: '#/components/messages/ListDeletedEvent'
          - $ref: '#/components/messages/SubscribedEvent'
          - $ref: '#/components/messages/UnsubscribedEvent'
          - $ref: '#/components/messages/PongEvent'
          - $ref: '#/components/messages/ErrorEvent'

components:
  messages:
    SubscribeCommand:
      payload:
        type: object
        properties:
          type:
            type: string
            const: subscribe
          listIds:
            type: array
            items:
              type: string
              format: uuid

    UnsubscribeCommand:
      payload:
        type: object
        properties:
          type:
            type: string
            const: unsubscribe
          listIds:
            type: array
            items:
              type: string
              format: uuid

    PingCommand:
      payload:
        type: object
        properties:
          type:
            type: string
            const: ping

    ItemAddedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: item:added
          listId:
            type: string
            format: uuid
          item:
            $ref: '#/components/schemas/ItemData'
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    ItemUpdatedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: item:updated
          listId:
            type: string
            format: uuid
          item:
            $ref: '#/components/schemas/ItemData'
          changes:
            type: array
            items:
              type: string
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    ItemCheckedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: item:checked
          listId:
            type: string
            format: uuid
          itemId:
            type: string
            format: uuid
          isChecked:
            type: boolean
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    ItemRemovedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: item:removed
          listId:
            type: string
            format: uuid
          itemId:
            type: string
            format: uuid
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    ListUpdatedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: list:updated
          list:
            $ref: '#/components/schemas/ListData'
          changes:
            type: array
            items:
              type: string
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    ListDeletedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: list:deleted
          listId:
            type: string
            format: uuid
          actor:
            $ref: '#/components/schemas/ActorInfo'
          timestamp:
            type: string
            format: date-time

    SubscribedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: subscribed
          listIds:
            type: array
            items:
              type: string
              format: uuid

    UnsubscribedEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: unsubscribed
          listIds:
            type: array
            items:
              type: string
              format: uuid

    PongEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: pong

    ErrorEvent:
      payload:
        type: object
        properties:
          type:
            type: string
            const: error
          message:
            type: string
          code:
            type: string

  schemas:
    ItemData:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        quantity:
          type: number
        unit:
          type: string
          nullable: true
        isChecked:
          type: boolean
        checkedByName:
          type: string
          nullable: true

    ListData:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        householdId:
          type: string
          format: uuid
          nullable: true
        isPersonal:
          type: boolean

    ActorInfo:
      type: object
      properties:
        id:
          type: string
          format: uuid
        displayName:
          type: string
```

**Step 2: Commit**

```bash
mkdir -p backend/src/main/resources/asyncapi
git add backend/src/main/resources/asyncapi/websocket.yaml
git commit -m "docs: add AsyncAPI spec for WebSocket events"
```

---

## Task 13: Final Verification

**Files:** None (verification only)

**Step 1: Run full test suite**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime && make test`
Expected: All tests pass (backend + frontend)

**Step 2: Run all linters**

Run: `cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime && make lint`
Expected: No lint errors

**Step 3: Manual smoke test**

Start the application:
```bash
cd /home/lars/Prosjekter/shopping-list/.worktrees/phase5-realtime && make dev
```

Test WebSocket connection using wscat or browser dev tools:
```bash
wscat -c "ws://localhost:8080/ws?token=<valid-jwt-token>"
```

Expected: Connection established, receives `subscribed` event with list IDs

---

## Summary

After completing all tasks, Phase 5A delivers:
- WebSocket endpoint at `/ws` with JWT authentication
- Event types for item and list changes
- Session manager tracking connections and subscriptions
- Event broadcaster that services call after mutations
- Auto-subscription to accessible lists on connect
- Subscribe/unsubscribe commands for manual control
- Unit tests for session manager
- OpenAPI documentation for WebSocket endpoint
- AsyncAPI documentation for event schemas

The frontend can now connect and receive real-time updates. Phase 5B will add the backend features needed for the new UI.
