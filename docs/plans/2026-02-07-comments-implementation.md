# Comments Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add real-time comment feeds to shopping lists and households.

**Architecture:** Polymorphic `Comments` table with `targetType` (LIST/HOUSEHOLD) + `targetId`. Backend `CommentService` + routes nested under `/lists/{id}/comments` and `/households/{id}/comments`. WebSocket events broadcast via `EventBroadcaster` with new household subscriptions in `WebSocketSessionManager`. Frontend `commentsSlice` + `CommentFeed` component.

**Tech Stack:** Kotlin/Ktor/Exposed (backend), React/Redux/TypeScript (frontend), WebSocket real-time, i18n (5 languages)

---

### Task 1: Domain Model — Comment Table + Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/Comment.kt`

**Implementation:**

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

enum class CommentTargetType {
    LIST,
    HOUSEHOLD,
}

object Comments : UUIDTable("comments") {
    val targetType = enumerationByName<CommentTargetType>("target_type", 20)
    val targetId = uuid("target_id").index()
    val author = reference("author_id", Accounts)
    val text = text("text")
    val editedAt = timestamp("edited_at").nullable()
    val createdAt = timestamp("created_at")

    init {
        index(isUnique = false, targetType, targetId, createdAt)
    }
}

class Comment(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Comment>(Comments)

    var targetType by Comments.targetType
    var targetId by Comments.targetId
    var author by Account referencedOn Comments.author
    var text by Comments.text
    var editedAt by Comments.editedAt
    var createdAt by Comments.createdAt
}
```

**Commit:** `feat: add Comment domain model`

---

### Task 2: CommentService

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/CommentService.kt`

**Implementation:**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Comment
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.domain.Comments
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class CommentService(
    private val db: Database,
) {
    fun create(
        targetType: CommentTargetType,
        targetId: UUID,
        authorId: UUID,
        text: String,
    ): Comment =
        transaction(db) {
            Comment.new {
                this.targetType = targetType
                this.targetId = targetId
                this.author = no.shoppinglist.domain.Account.findById(authorId)
                    ?: throw IllegalArgumentException("Account not found")
                this.text = text.take(2000)
                this.createdAt = Instant.now()
            }
        }

    fun findByTarget(
        targetType: CommentTargetType,
        targetId: UUID,
        limit: Int = 50,
        offset: Long = 0,
    ): List<Comment> =
        transaction(db) {
            Comment
                .find { (Comments.targetType eq targetType) and (Comments.targetId eq targetId) }
                .orderBy(Comments.createdAt to SortOrder.ASC)
                .limit(limit)
                .offset(offset)
                .toList()
        }

    fun findById(commentId: UUID): Comment? =
        transaction(db) {
            Comment.findById(commentId)
        }

    fun update(
        commentId: UUID,
        accountId: UUID,
        newText: String,
    ): Comment? =
        transaction(db) {
            val comment = Comment.findById(commentId) ?: return@transaction null
            if (comment.author.id.value != accountId) return@transaction null
            comment.text = newText.take(2000)
            comment.editedAt = Instant.now()
            comment
        }

    fun delete(
        commentId: UUID,
        accountId: UUID,
    ): Boolean =
        transaction(db) {
            val comment = Comment.findById(commentId) ?: return@transaction false
            if (comment.author.id.value != accountId) return@transaction false
            comment.delete()
            true
        }

    fun deleteByTarget(
        targetType: CommentTargetType,
        targetId: UUID,
    ): Int =
        transaction(db) {
            val comments = Comment.find {
                (Comments.targetType eq targetType) and (Comments.targetId eq targetId)
            }
            val count = comments.count().toInt()
            comments.forEach { it.delete() }
            count
        }
}
```

**Commit:** `feat: add CommentService with CRUD operations`

---

### Task 3: WebSocket Events — Comment event types

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketEvent.kt`

**Append after the existing ListDeletedEvent class (after line 89):**

```kotlin
// Comment events
@Serializable
@SerialName("comment:added")
data class CommentAddedEvent(
    override val type: String = "comment:added",
    val targetType: String,
    val targetId: String,
    val comment: CommentData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("comment:updated")
data class CommentUpdatedEvent(
    override val type: String = "comment:updated",
    val targetType: String,
    val targetId: String,
    val commentId: String,
    val text: String,
    val editedAt: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("comment:deleted")
data class CommentDeletedEvent(
    override val type: String = "comment:deleted",
    val targetType: String,
    val targetId: String,
    val commentId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// Comment data transfer object
@Serializable
data class CommentData(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val editedAt: String?,
    val createdAt: String,
)
```

**Commit:** `feat: add WebSocket event types for comments`

---

### Task 4: WebSocket — Household subscriptions in SessionManager

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/websocket/WebSocketSessionManager.kt`

**Add household subscription maps and methods.** After line 18 (`private val accountSubscriptions`), add:

```kotlin
// householdId -> set of accountIds subscribed to that household
private val householdSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

// accountId -> set of householdIds they're subscribed to
private val accountHouseholdSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
```

**In `removeSession`, after line 43** (after cleaning up list subscriptions), add household cleanup:

```kotlin
accountHouseholdSubscriptions[accountId]?.forEach { householdId ->
    householdSubscriptions[householdId]?.remove(accountId)
}
accountHouseholdSubscriptions.remove(accountId)
```

**Add new methods before `getConnectedAccountCount()`:**

```kotlin
fun subscribeToHousehold(
    accountId: UUID,
    householdId: UUID,
) {
    householdSubscriptions.computeIfAbsent(householdId) { mutableSetOf() }.add(accountId)
    accountHouseholdSubscriptions.computeIfAbsent(accountId) { mutableSetOf() }.add(householdId)
}

fun unsubscribeFromHousehold(
    accountId: UUID,
    householdId: UUID,
) {
    householdSubscriptions[householdId]?.remove(accountId)
    accountHouseholdSubscriptions[accountId]?.remove(householdId)
}

suspend fun broadcastToHousehold(
    householdId: UUID,
    event: WebSocketEvent,
    excludeAccountId: UUID? = null,
) {
    val subscribers = householdSubscriptions[householdId] ?: return
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
```

**Commit:** `feat: add household subscriptions to WebSocketSessionManager`

---

### Task 5: WebSocket — Auto-subscribe to households on connect

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/WebSocketRoutes.kt`

**Add `HouseholdService` parameter** to the `webSocketRoutes` function signature (line 30-34):

```kotlin
fun Route.webSocketRoutes(
    jwtConfig: JwtConfig,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
    householdService: HouseholdService,
)
```

**After line 66** (after auto-subscribing to lists, before sending confirmation), add:

```kotlin
// Auto-subscribe to all households
val households = householdService.findByAccountId(accountId)
households.forEach { household ->
    sessionManager.subscribeToHousehold(accountId, household.id.value)
}
```

**Update call site in `Application.kt`** (line 165) to pass `householdService`:

```kotlin
webSocketRoutes(authConfig.jwt, sessionManager, services.shoppingListService, services.householdService)
```

**Add import** to `WebSocketRoutes.kt`:
```kotlin
import no.shoppinglist.service.HouseholdService
```

**Commit:** `feat: auto-subscribe to household WebSocket channels on connect`

---

### Task 6: EventBroadcaster — Comment broadcast methods

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/websocket/EventBroadcaster.kt`

**Add import:**
```kotlin
import no.shoppinglist.domain.Comment
import no.shoppinglist.domain.CommentTargetType
```

**Add three methods before `toItemData()`:**

```kotlin
fun broadcastCommentAdded(
    comment: Comment,
    actorId: UUID,
) {
    scope.launch {
        val (targetType, targetId, event) =
            transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                val event =
                    CommentAddedEvent(
                        targetType = comment.targetType.name,
                        targetId = comment.targetId.toString(),
                        comment = comment.toCommentData(),
                        actor = actor.toActorInfo(),
                    )
                Triple(comment.targetType, comment.targetId, event)
            } ?: return@launch

        broadcastCommentEvent(targetType, targetId, event, actorId)
    }
}

fun broadcastCommentUpdated(
    comment: Comment,
    actorId: UUID,
) {
    scope.launch {
        val (targetType, targetId, event) =
            transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                val event =
                    CommentUpdatedEvent(
                        targetType = comment.targetType.name,
                        targetId = comment.targetId.toString(),
                        commentId = comment.id.value.toString(),
                        text = comment.text,
                        editedAt = comment.editedAt.toString(),
                        actor = actor.toActorInfo(),
                    )
                Triple(comment.targetType, comment.targetId, event)
            } ?: return@launch

        broadcastCommentEvent(targetType, targetId, event, actorId)
    }
}

fun broadcastCommentDeleted(
    targetType: CommentTargetType,
    targetId: UUID,
    commentId: UUID,
    actorId: UUID,
) {
    scope.launch {
        val event =
            transaction {
                val actor = Account.findById(actorId) ?: return@transaction null
                CommentDeletedEvent(
                    targetType = targetType.name,
                    targetId = targetId.toString(),
                    commentId = commentId.toString(),
                    actor = actor.toActorInfo(),
                )
            } ?: return@launch

        broadcastCommentEvent(targetType, targetId, event, actorId)
    }
}

private suspend fun broadcastCommentEvent(
    targetType: CommentTargetType,
    targetId: UUID,
    event: WebSocketEvent,
    excludeAccountId: UUID,
) {
    when (targetType) {
        CommentTargetType.LIST -> sessionManager.broadcastToList(targetId, event, excludeAccountId)
        CommentTargetType.HOUSEHOLD -> sessionManager.broadcastToHousehold(targetId, event, excludeAccountId)
    }
}

private fun Comment.toCommentData(): CommentData =
    CommentData(
        id = id.value.toString(),
        text = text,
        authorId = author.id.value.toString(),
        authorName = author.displayName,
        authorAvatarUrl = author.avatarUrl,
        editedAt = editedAt?.toString(),
        createdAt = createdAt.toString(),
    )
```

**Commit:** `feat: add comment broadcast methods to EventBroadcaster`

---

### Task 7: Comment Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/CommentRoutes.kt`

**Implementation:**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.service.CommentService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.websocket.EventBroadcaster
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateCommentRequest(
    val text: String,
)

@Serializable
data class UpdateCommentRequest(
    val text: String,
)

@Serializable
data class CommentResponse(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val editedAt: String?,
    val createdAt: String,
)

@Suppress("LongMethod")
fun Route.listCommentRoutes(
    commentService: CommentService,
    shoppingListService: ShoppingListService,
    eventBroadcaster: EventBroadcaster,
) {
    authenticate("auth-jwt") {
        route("/lists/{id}/comments") {
            get {
                val accountId =
                    call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val listId =
                    call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                val permission = shoppingListService.getPermission(listId, accountId, null)
                if (permission == null) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

                val comments = commentService.findByTarget(CommentTargetType.LIST, listId, limit, offset)
                call.respond(comments.map { it.toResponse() })
            }

            post {
                val accountId =
                    call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val listId =
                    call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@post call.respond(HttpStatusCode.BadRequest)

                val permission = shoppingListService.getPermission(listId, accountId, null)
                if (permission == null) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }

                val request = call.receive<CreateCommentRequest>()
                if (request.text.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                }

                val comment = commentService.create(CommentTargetType.LIST, listId, accountId, request.text)
                eventBroadcaster.broadcastCommentAdded(comment, accountId)
                call.respond(HttpStatusCode.Created, transaction { comment.toResponse() })
            }

            route("/{commentId}") {
                patch {
                    val accountId =
                        call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                    val commentId =
                        call.parameters["commentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@patch call.respond(HttpStatusCode.BadRequest)

                    val request = call.receive<UpdateCommentRequest>()
                    if (request.text.isBlank()) {
                        return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                    }

                    val updated = commentService.update(commentId, accountId, request.text)
                        ?: return@patch call.respond(HttpStatusCode.NotFound)

                    eventBroadcaster.broadcastCommentUpdated(updated, accountId)
                    call.respond(transaction { updated.toResponse() })
                }

                delete {
                    val accountId =
                        call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                    val commentId =
                        call.parameters["commentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    val comment = commentService.findById(commentId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)

                    val targetType = transaction { comment.targetType }
                    val targetId = transaction { comment.targetId }

                    val deleted = commentService.delete(commentId, accountId)
                    if (!deleted) {
                        return@delete call.respond(HttpStatusCode.Forbidden)
                    }

                    eventBroadcaster.broadcastCommentDeleted(targetType, targetId, commentId, accountId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

@Suppress("LongMethod")
fun Route.householdCommentRoutes(
    commentService: CommentService,
    householdService: HouseholdService,
    eventBroadcaster: EventBroadcaster,
) {
    authenticate("auth-jwt") {
        route("/households/{id}/comments") {
            get {
                val accountId =
                    call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val householdId =
                    call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!householdService.isMember(householdId, accountId)) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

                val comments = commentService.findByTarget(CommentTargetType.HOUSEHOLD, householdId, limit, offset)
                call.respond(comments.map { it.toResponse() })
            }

            post {
                val accountId =
                    call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val householdId =
                    call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!householdService.isMember(householdId, accountId)) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }

                val request = call.receive<CreateCommentRequest>()
                if (request.text.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                }

                val comment = commentService.create(CommentTargetType.HOUSEHOLD, householdId, accountId, request.text)
                eventBroadcaster.broadcastCommentAdded(comment, accountId)
                call.respond(HttpStatusCode.Created, transaction { comment.toResponse() })
            }

            route("/{commentId}") {
                patch {
                    val accountId =
                        call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                    val commentId =
                        call.parameters["commentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@patch call.respond(HttpStatusCode.BadRequest)

                    val request = call.receive<UpdateCommentRequest>()
                    if (request.text.isBlank()) {
                        return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                    }

                    val updated = commentService.update(commentId, accountId, request.text)
                        ?: return@patch call.respond(HttpStatusCode.NotFound)

                    eventBroadcaster.broadcastCommentUpdated(updated, accountId)
                    call.respond(transaction { updated.toResponse() })
                }

                delete {
                    val accountId =
                        call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                    val commentId =
                        call.parameters["commentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    val comment = commentService.findById(commentId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)

                    val targetType = transaction { comment.targetType }
                    val targetId = transaction { comment.targetId }

                    val deleted = commentService.delete(commentId, accountId)
                    if (!deleted) {
                        return@delete call.respond(HttpStatusCode.Forbidden)
                    }

                    eventBroadcaster.broadcastCommentDeleted(targetType, targetId, commentId, accountId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun no.shoppinglist.domain.Comment.toResponse() =
    CommentResponse(
        id = id.value.toString(),
        text = text,
        authorId = author.id.value.toString(),
        authorName = author.displayName,
        authorAvatarUrl = author.avatarUrl,
        editedAt = editedAt?.toString(),
        createdAt = createdAt.toString(),
    )
```

**Commit:** `feat: add comment REST routes for lists and households`

---

### Task 8: Wire CommentService and Routes into Application.kt

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Changes:**

1. Add import:
```kotlin
import no.shoppinglist.routes.householdCommentRoutes
import no.shoppinglist.routes.listCommentRoutes
import no.shoppinglist.service.CommentService
```

2. Add `commentService` to `Services` data class (after `preferencesService`):
```kotlin
val commentService: CommentService,
```

3. Add to `createServices()` (after `preferencesService`):
```kotlin
commentService = CommentService(db),
```

4. In `configureRouting`, add after `preferencesRoutes`:
```kotlin
listCommentRoutes(services.commentService, services.shoppingListService, eventBroadcaster)
householdCommentRoutes(services.commentService, services.householdService, eventBroadcaster)
```

**Commit:** `feat: wire CommentService and routes into Application`

---

### Task 9: Cascade deletes — Clean up comments on list/household deletion

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt` — in the `delete` method, add comment cleanup before deleting the list
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/HouseholdService.kt` — in the `delete` method, add comment cleanup before deleting the household

**For ShoppingListService**, add `CommentService` as a constructor parameter or add direct cleanup. Simplest: add direct SQL deletion in the existing `delete` method, by adding to the transaction:

```kotlin
// In delete method, before deleting items/shares/list:
Comments.deleteWhere { (Comments.targetType eq CommentTargetType.LIST) and (Comments.targetId eq listId) }
```

**For HouseholdService**, same pattern:
```kotlin
// In delete method, before deleting memberships/household:
Comments.deleteWhere { (Comments.targetType eq CommentTargetType.HOUSEHOLD) and (Comments.targetId eq householdId) }
```

Both need imports:
```kotlin
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.CommentTargetType
```

**Commit:** `feat: cascade delete comments on list/household deletion`

---

### Task 10: Frontend — commentsSlice

**Files:**
- Create: `web/src/store/commentsSlice.ts`

**Implementation:**

```typescript
import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface Comment {
  id: string;
  text: string;
  authorId: string;
  authorName: string;
  authorAvatarUrl: string | null;
  editedAt: string | null;
  createdAt: string;
}

export interface CommentsState {
  commentsByTarget: Record<string, Comment[]>;
  loading: boolean;
}

const initialState: CommentsState = {
  commentsByTarget: {},
  loading: false,
};

function targetKey(targetType: string, targetId: string): string {
  return `${targetType}:${targetId}`;
}

const commentsSlice = createSlice({
  name: 'comments',
  initialState,
  reducers: {
    setComments(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; comments: Comment[] }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      state.commentsByTarget[key] = action.payload.comments;
    },
    addComment(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; comment: Comment }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      if (!state.commentsByTarget[key]) {
        state.commentsByTarget[key] = [];
      }
      const existing = state.commentsByTarget[key].find((c) => c.id === action.payload.comment.id);
      if (!existing) {
        state.commentsByTarget[key].push(action.payload.comment);
      }
    },
    updateComment(
      state,
      action: PayloadAction<{
        targetType: string;
        targetId: string;
        commentId: string;
        text: string;
        editedAt: string;
      }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      const comments = state.commentsByTarget[key];
      if (comments) {
        const comment = comments.find((c) => c.id === action.payload.commentId);
        if (comment) {
          comment.text = action.payload.text;
          comment.editedAt = action.payload.editedAt;
        }
      }
    },
    removeComment(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; commentId: string }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      const comments = state.commentsByTarget[key];
      if (comments) {
        state.commentsByTarget[key] = comments.filter((c) => c.id !== action.payload.commentId);
      }
    },
    setCommentsLoading(state, action: PayloadAction<boolean>) {
      state.loading = action.payload;
    },
    clearComments(state, action: PayloadAction<{ targetType: string; targetId: string }>) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      delete state.commentsByTarget[key];
    },
  },
});

export const { setComments, addComment, updateComment, removeComment, setCommentsLoading, clearComments } =
  commentsSlice.actions;

export default commentsSlice.reducer;
```

**Commit:** `feat: add commentsSlice for frontend state management`

---

### Task 11: Register commentsSlice in store

**Files:**
- Modify: `web/src/store/store.ts`

**Add import and reducer:**

```typescript
import commentsReducer from './commentsSlice';
```

Add to reducer object:
```typescript
comments: commentsReducer,
```

**Commit:** `feat: register commentsSlice in Redux store`

---

### Task 12: Frontend WebSocket — Comment event types + bridge handlers

**Files:**
- Modify: `web/src/services/websocket.ts`
- Modify: `web/src/services/websocketBridge.ts`

**In `websocket.ts`:**

1. Add to `WebSocketEventType` union (after `'list:deleted'`):
```typescript
| 'comment:added'
| 'comment:updated'
| 'comment:deleted'
```

2. Add event interfaces (after `ListDeletedEvent`):
```typescript
export interface CommentAddedEvent {
  type: 'comment:added';
  targetType: string;
  targetId: string;
  comment: {
    id: string;
    text: string;
    authorId: string;
    authorName: string;
    authorAvatarUrl: string | null;
    editedAt: string | null;
    createdAt: string;
  };
  actor: ActorInfo;
  timestamp: string;
}

export interface CommentUpdatedEvent {
  type: 'comment:updated';
  targetType: string;
  targetId: string;
  commentId: string;
  text: string;
  editedAt: string;
  actor: ActorInfo;
  timestamp: string;
}

export interface CommentDeletedEvent {
  type: 'comment:deleted';
  targetType: string;
  targetId: string;
  commentId: string;
  actor: ActorInfo;
  timestamp: string;
}
```

3. Add to `WebSocketEvent` union type:
```typescript
| CommentAddedEvent
| CommentUpdatedEvent
| CommentDeletedEvent
```

**In `websocketBridge.ts`:**

1. Add imports:
```typescript
import {
  addComment,
  updateComment,
  removeComment,
} from '../store/commentsSlice';
import type {
  CommentAddedEvent,
  CommentUpdatedEvent,
  CommentDeletedEvent,
} from './websocket';
```

2. Add cases to `handleWebSocketEvent` switch:
```typescript
case 'comment:added':
  handleCommentAdded(dispatch, event);
  break;
case 'comment:updated':
  handleCommentUpdated(dispatch, event);
  break;
case 'comment:deleted':
  handleCommentDeleted(dispatch, event);
  break;
```

3. Add handler functions:
```typescript
function handleCommentAdded(dispatch: AppDispatch, event: CommentAddedEvent): void {
  dispatch(
    addComment({
      targetType: event.targetType,
      targetId: event.targetId,
      comment: {
        id: event.comment.id,
        text: event.comment.text,
        authorId: event.comment.authorId,
        authorName: event.comment.authorName,
        authorAvatarUrl: event.comment.authorAvatarUrl,
        editedAt: event.comment.editedAt,
        createdAt: event.comment.createdAt,
      },
    })
  );
}

function handleCommentUpdated(dispatch: AppDispatch, event: CommentUpdatedEvent): void {
  dispatch(
    updateComment({
      targetType: event.targetType,
      targetId: event.targetId,
      commentId: event.commentId,
      text: event.text,
      editedAt: event.editedAt,
    })
  );
}

function handleCommentDeleted(dispatch: AppDispatch, event: CommentDeletedEvent): void {
  dispatch(
    removeComment({
      targetType: event.targetType,
      targetId: event.targetId,
      commentId: event.commentId,
    })
  );
}
```

**Commit:** `feat: add comment WebSocket event types and bridge handlers`

---

### Task 13: CommentFeed UI Component

**Files:**
- Create: `web/src/components/comments/CommentFeed.tsx`

**Implementation:** A reusable comment feed component that loads comments via REST on mount and handles real-time updates via Redux state. Features: post new comments, inline edit own comments, delete with confirmation, auto-scroll on new comments, timestamps, edited indicator.

The component should:
- Accept props: `targetType: 'LIST' | 'HOUSEHOLD'`, `targetId: string`
- Use `useAppSelector` to read from `commentsSlice` keyed by `${targetType}:${targetId}`
- Fetch comments on mount via `GET /lists/{id}/comments` or `GET /households/{id}/comments`
- Post via `POST .../comments`
- Edit via `PATCH .../comments/{id}`
- Delete via `DELETE .../comments/{id}`
- Dispatch optimistic updates to Redux
- Auto-scroll to bottom when new comments arrive
- Show author avatar initial, name, timestamp, "(edited)" badge
- Inline edit form for own comments
- Confirm before delete
- Use i18n translations throughout

**Commit:** `feat: add CommentFeed UI component`

---

### Task 14: Integrate CommentFeed into ShoppingListView

**Files:**
- Modify: `web/src/components/shopping-list/ShoppingListView.tsx`

**Add below the items section** (before the closing `</div>`), add:

```tsx
import CommentFeed from '../comments/CommentFeed';

{/* After the EmptyState / items section */}
<div className="mt-6">
  <CommentFeed targetType="LIST" targetId={listId} />
</div>
```

**Commit:** `feat: integrate CommentFeed into ShoppingListView`

---

### Task 15: Integrate CommentFeed into HouseholdDetail

**Files:**
- Modify: `web/src/components/household/HouseholdDetail.tsx`

**Add after the members section:**

```tsx
import CommentFeed from '../comments/CommentFeed';

{/* After the members card, before the closing </div> */}
<div className="mt-6">
  <CommentFeed targetType="HOUSEHOLD" targetId={householdId} />
</div>
```

**Commit:** `feat: integrate CommentFeed into HouseholdDetail`

---

### Task 16: i18n — Add comment translations to all 5 languages

**Files:**
- Modify: `web/src/i18n/locales/en/translation.json`
- Modify: `web/src/i18n/locales/nb/translation.json`
- Modify: `web/src/i18n/locales/nn/translation.json`
- Modify: `web/src/i18n/locales/se/translation.json`
- Modify: `web/src/i18n/locales/tl/translation.json`

**Add `"comments"` section to each:**

English (`en`):
```json
"comments": {
  "title": "Comments",
  "placeholder": "Add a comment...",
  "send": "Send",
  "edit": "Edit",
  "delete": "Delete",
  "edited": "(edited)",
  "confirmDelete": "Delete this comment?",
  "noComments": "No comments yet",
  "failedToPost": "Failed to post comment",
  "failedToEdit": "Failed to edit comment",
  "failedToDelete": "Failed to delete comment",
  "failedToLoad": "Failed to load comments",
  "editing": "Editing...",
  "save": "Save",
  "cancelEdit": "Cancel"
}
```

Norwegian Bokmål (`nb`):
```json
"comments": {
  "title": "Kommentarer",
  "placeholder": "Legg til en kommentar...",
  "send": "Send",
  "edit": "Rediger",
  "delete": "Slett",
  "edited": "(redigert)",
  "confirmDelete": "Slette denne kommentaren?",
  "noComments": "Ingen kommentarer ennå",
  "failedToPost": "Kunne ikke legge til kommentar",
  "failedToEdit": "Kunne ikke redigere kommentar",
  "failedToDelete": "Kunne ikke slette kommentar",
  "failedToLoad": "Kunne ikke laste kommentarer",
  "editing": "Redigerer...",
  "save": "Lagre",
  "cancelEdit": "Avbryt"
}
```

Norwegian Nynorsk (`nn`):
```json
"comments": {
  "title": "Kommentarar",
  "placeholder": "Legg til ein kommentar...",
  "send": "Send",
  "edit": "Rediger",
  "delete": "Slett",
  "edited": "(redigert)",
  "confirmDelete": "Slette denne kommentaren?",
  "noComments": "Ingen kommentarar enno",
  "failedToPost": "Kunne ikkje leggje til kommentar",
  "failedToEdit": "Kunne ikkje redigere kommentar",
  "failedToDelete": "Kunne ikkje slette kommentar",
  "failedToLoad": "Kunne ikkje laste kommentarar",
  "editing": "Redigerer...",
  "save": "Lagre",
  "cancelEdit": "Avbryt"
}
```

Northern Sami (`se`):
```json
"comments": {
  "title": "Kommeanttat",
  "placeholder": "Lasit kommeanta...",
  "send": "Sádde",
  "edit": "Rievdat",
  "delete": "Sihko",
  "edited": "(rievdaduvvon)",
  "confirmDelete": "Sihkot dán kommeanta?",
  "noComments": "Eai leat kommeanttat velá",
  "failedToPost": "Ii lihkostuvvan lasihit kommeanta",
  "failedToEdit": "Ii lihkostuvvan rievdadit kommeanta",
  "failedToDelete": "Ii lihkostuvvan sihkkut kommeanta",
  "failedToLoad": "Ii lihkostuvvan viežžat kommeanttat",
  "editing": "Rievdadeamen...",
  "save": "Vurke",
  "cancelEdit": "Gasket"
}
```

Tagalog (`tl`):
```json
"comments": {
  "title": "Mga Komento",
  "placeholder": "Magdagdag ng komento...",
  "send": "Ipadala",
  "edit": "I-edit",
  "delete": "Burahin",
  "edited": "(na-edit)",
  "confirmDelete": "Burahin ang komentong ito?",
  "noComments": "Wala pang mga komento",
  "failedToPost": "Hindi naipadala ang komento",
  "failedToEdit": "Hindi na-edit ang komento",
  "failedToDelete": "Hindi nabura ang komento",
  "failedToLoad": "Hindi nai-load ang mga komento",
  "editing": "Ine-edit...",
  "save": "I-save",
  "cancelEdit": "Kanselahin"
}
```

**Commit:** `feat: add comment i18n translations for all 5 languages`

---

### Task 17: Run tests and fix issues

Run `make test` and `make lint`. Fix any compilation errors, test failures, or lint issues.

**Commit:** `fix: resolve test and lint issues for comments feature`
