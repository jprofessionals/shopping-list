# Shared List Full Access Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable CHECK and WRITE operations on shared shopping lists via link token, matching permission levels.

**Architecture:** Add mutation endpoints to `SharedAccessRoutes.kt` using a token-validation helper that reuses `ListItemService`. Refactor frontend `ShoppingListView` to accept a `shareToken` + `permission` prop so the shared view reuses the same UI. Replace day-based link expiry with hour-based presets (1h–7d).

**Tech Stack:** Kotlin/Ktor (backend), React/TypeScript (frontend), Kotest + TestContainers (backend tests), Vitest + React Testing Library (frontend tests)

---

### Task 1: Backend — Token Validation Helper + Check Endpoint

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt`
- Test: `backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt`

**Step 1: Write failing tests for POST /shared/{token}/items/{itemId}/check**

Add these tests to `SharedAccessRoutesTest.kt`. The existing test class uses `FunSpec` with `testApplication`. Add after the last existing test:

```kotlin
// Add these imports at the top:
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

// Add these tests:
test("POST /shared/:token/items/:itemId/check toggles item for CHECK permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 2.0,
            unit = "L",
            barcode = null,
            createdById = account.id.value,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.CHECK,
            expirationDays = 7,
        )

        val response = client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"isChecked": true}""")
        }

        response.status shouldBe HttpStatusCode.OK
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["isChecked"]?.jsonPrimitive?.content shouldBe "true"
    }
}

test("POST /shared/:token/items/:itemId/check returns 403 for READ permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 1.0,
            unit = null,
            barcode = null,
            createdById = account.id.value,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.READ,
            expirationDays = 7,
        )

        val response = client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"isChecked": true}""")
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }
}

test("POST /shared/:token/items/:itemId/check returns 410 for expired token") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 1.0,
            unit = null,
            barcode = null,
            createdById = account.id.value,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.CHECK,
            expirationDays = -1,
        )

        val response = client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"isChecked": true}""")
        }

        response.status shouldBe HttpStatusCode.Gone
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "*SharedAccessRoutesTest*" -q`
Expected: 3 new tests FAIL (405 Method Not Allowed or similar — endpoints don't exist)

**Step 3: Implement validateShareToken helper and check endpoint**

In `SharedAccessRoutes.kt`, add a data class and helper function, then add the check route:

```kotlin
// Add imports:
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.routes.shoppinglist.CreateItemRequest
import no.shoppinglist.routes.shoppinglist.UpdateItemRequest
import no.shoppinglist.routes.shoppinglist.ITEM_NAME_MIN_LENGTH
import no.shoppinglist.routes.shoppinglist.ITEM_NAME_MAX_LENGTH
import java.util.UUID

// Add data class for validated share context:
data class ShareContext(
    val listId: UUID,
    val permission: SharePermission,
)

// Add helper — returns null and responds with error if invalid:
private suspend fun io.ktor.server.routing.RoutingContext.validateShareToken(
    listShareService: ListShareService,
): ShareContext? {
    val token = call.parameters["token"]
        ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return null
        }

    val share = listShareService.findByToken(token)
    if (share == null) {
        if (listShareService.isTokenExpired(token)) {
            call.respond(HttpStatusCode.Gone, mapOf("error" to "This link has expired"))
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
        return null
    }

    val listId = transaction { share.list.id.value }
    return ShareContext(listId = listId, permission = share.permission)
}

// Update sharedAccessRoutes to add item routes:
fun Route.sharedAccessRoutes(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    route("/shared/{token}") {
        getSharedListRoute(listShareService, listItemService)
        route("/items") {
            route("/{itemId}") {
                checkItemRoute(listShareService, listItemService)
            }
        }
    }
}

// Add the check route:
private fun Route.checkItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    post("/check") {
        val ctx = validateShareToken(listShareService) ?: return@post
        if (ctx.permission != SharePermission.CHECK && ctx.permission != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = call.parameters["itemId"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)

        // Verify item belongs to the shared list
        val item = listItemService.findById(itemId)
        if (item == null || transaction { item.list.id.value } != ctx.listId) {
            return@post call.respond(HttpStatusCode.NotFound)
        }

        // toggleCheck needs an accountId for checkedBy — use a null-safe approach
        // For shared access, we pass a dummy since there's no authenticated user
        val toggled = listItemService.toggleCheck(itemId, ctx.listId)
            ?: return@post call.respond(HttpStatusCode.NotFound)

        call.respond(HttpStatusCode.OK, transaction {
            SharedItemResponse(
                id = toggled.id.value.toString(),
                name = toggled.name,
                quantity = toggled.quantity,
                unit = toggled.unit,
                isChecked = toggled.isChecked,
            )
        })
    }
}
```

Note: `toggleCheck` takes an `accountId` parameter used for `checkedBy`. For anonymous shared access, we need to handle this. Two options:
1. Add an overload `toggleCheck(id: UUID)` without accountId (sets `checkedBy = null`)
2. Pass the list ID (it will look up an account that doesn't exist and set `checkedBy = null`)

The cleanest approach: add a new method to `ListItemService`:

```kotlin
// In ListItemService.kt, add:
fun toggleCheckAnonymous(id: UUID): ListItem? =
    transaction(db) {
        val item = ListItem.findById(id) ?: return@transaction null
        if (item.isChecked) {
            item.isChecked = false
            item.checkedBy = null
        } else {
            item.isChecked = true
            item.checkedBy = null
        }
        item.updatedAt = Instant.now()
        item
    }
```

Then use `listItemService.toggleCheckAnonymous(itemId)` in the check route instead.

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "*SharedAccessRoutesTest*" -q`
Expected: All 8 tests PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt \
       backend/src/main/kotlin/no/shoppinglist/service/ListItemService.kt \
       backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt
git commit -m "feat: add shared list check/uncheck endpoint with token validation"
```

---

### Task 2: Backend — WRITE Mutation Endpoints (Add, Edit, Delete, Clear Checked)

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt`
- Test: `backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt`

**Step 1: Write failing tests for WRITE endpoints**

Add to `SharedAccessRoutesTest.kt`:

```kotlin
// Add these imports:
import io.ktor.client.request.delete
import io.ktor.client.request.patch

test("POST /shared/:token/items adds item for WRITE permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.WRITE,
            expirationDays = 7,
        )

        val response = client.post("/shared/${share.linkToken}/items") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "Eggs", "quantity": 12.0, "unit": "pcs"}""")
        }

        response.status shouldBe HttpStatusCode.Created
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["name"]?.jsonPrimitive?.content shouldBe "Eggs"
        json["quantity"]?.jsonPrimitive?.content shouldBe "12.0"
    }
}

test("POST /shared/:token/items returns 403 for CHECK permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.CHECK,
            expirationDays = 7,
        )

        val response = client.post("/shared/${share.linkToken}/items") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "Eggs", "quantity": 1.0}""")
        }

        response.status shouldBe HttpStatusCode.Forbidden
    }
}

test("PATCH /shared/:token/items/:itemId updates item for WRITE permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 1.0,
            unit = null,
            barcode = null,
            createdById = account.id.value,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.WRITE,
            expirationDays = 7,
        )

        val response = client.patch("/shared/${share.linkToken}/items/${item.id.value}") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "Oat Milk", "quantity": 2.0, "unit": "L"}""")
        }

        response.status shouldBe HttpStatusCode.OK
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["name"]?.jsonPrimitive?.content shouldBe "Oat Milk"
        json["quantity"]?.jsonPrimitive?.content shouldBe "2.0"
        json["unit"]?.jsonPrimitive?.content shouldBe "L"
    }
}

test("DELETE /shared/:token/items/:itemId deletes item for WRITE permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 1.0,
            unit = null,
            barcode = null,
            createdById = account.id.value,
        )
        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.WRITE,
            expirationDays = 7,
        )

        val response = client.delete("/shared/${share.linkToken}/items/${item.id.value}")

        response.status shouldBe HttpStatusCode.NoContent
    }
}

test("DELETE /shared/:token/items/checked clears checked items for WRITE permission") {
    testApplication {
        application { testModule(this) }

        val account = accountService.createLocal(
            email = "test@example.com",
            displayName = "Test User",
            password = "password123",
        )
        val list = shoppingListService.create(
            name = "Groceries",
            ownerId = account.id.value,
            householdId = null,
            isPersonal = false,
        )
        val item = listItemService.create(
            listId = list.id.value,
            name = "Milk",
            quantity = 1.0,
            unit = null,
            barcode = null,
            createdById = account.id.value,
        )
        listItemService.toggleCheck(item.id.value, account.id.value)

        val share = listShareService.createLinkShare(
            listId = list.id.value,
            permission = SharePermission.WRITE,
            expirationDays = 7,
        )

        val response = client.delete("/shared/${share.linkToken}/items/checked")

        response.status shouldBe HttpStatusCode.OK
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val deletedIds = json["deletedItemIds"]?.jsonArray
        deletedIds?.size shouldBe 1
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "*SharedAccessRoutesTest*" -q`
Expected: 5 new tests FAIL

**Step 3: Implement WRITE endpoints**

Add to `SharedAccessRoutes.kt` — expand the `sharedAccessRoutes` function and add new route functions:

```kotlin
fun Route.sharedAccessRoutes(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    route("/shared/{token}") {
        getSharedListRoute(listShareService, listItemService)
        route("/items") {
            addItemRoute(listShareService, listItemService)
            clearCheckedRoute(listShareService, listItemService)
            route("/{itemId}") {
                checkItemRoute(listShareService, listItemService)
                updateItemRoute(listShareService, listItemService)
                deleteItemRoute(listShareService, listItemService)
            }
        }
    }
}

private fun Route.addItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    post {
        val ctx = validateShareToken(listShareService) ?: return@post
        if (ctx.permission != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<CreateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Item name must be between $ITEM_NAME_MIN_LENGTH and $ITEM_NAME_MAX_LENGTH characters"),
            )
        }

        val item = listItemService.createAnonymous(ctx.listId, request.name, request.quantity, request.unit)
        call.respond(HttpStatusCode.Created, transaction {
            SharedItemResponse(
                id = item.id.value.toString(),
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                isChecked = item.isChecked,
            )
        })
    }
}

private fun Route.updateItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    patch {
        val ctx = validateShareToken(listShareService) ?: return@patch
        if (ctx.permission != SharePermission.WRITE) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = call.parameters["itemId"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@patch call.respond(HttpStatusCode.BadRequest)

        val item = listItemService.findById(itemId)
        if (item == null || transaction { item.list.id.value } != ctx.listId) {
            return@patch call.respond(HttpStatusCode.NotFound)
        }

        val request = call.receive<UpdateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@patch call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Item name must be between $ITEM_NAME_MIN_LENGTH and $ITEM_NAME_MAX_LENGTH characters"),
            )
        }

        val updated = listItemService.update(itemId, request.name, request.quantity, request.unit)
            ?: return@patch call.respond(HttpStatusCode.NotFound)

        call.respond(HttpStatusCode.OK, transaction {
            SharedItemResponse(
                id = updated.id.value.toString(),
                name = updated.name,
                quantity = updated.quantity,
                unit = updated.unit,
                isChecked = updated.isChecked,
            )
        })
    }
}

private fun Route.deleteItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    delete {
        val ctx = validateShareToken(listShareService) ?: return@delete
        if (ctx.permission != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = call.parameters["itemId"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val item = listItemService.findById(itemId)
        if (item == null || transaction { item.list.id.value } != ctx.listId) {
            return@delete call.respond(HttpStatusCode.NotFound)
        }

        listItemService.delete(itemId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.clearCheckedRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    delete("/checked") {
        val ctx = validateShareToken(listShareService) ?: return@delete
        if (ctx.permission != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val deletedIds = listItemService.deleteCheckedItems(ctx.listId)
        call.respond(mapOf("deletedItemIds" to deletedIds.map { it.toString() }))
    }
}
```

Also add `createAnonymous` to `ListItemService.kt` (creates item without a `createdBy` account):

```kotlin
fun createAnonymous(
    listId: UUID,
    name: String,
    quantity: Double,
    unit: String?,
): ListItem =
    transaction(db) {
        val list = ShoppingList.findById(listId)
            ?: throw IllegalArgumentException("List not found: $listId")
        val now = Instant.now()

        ListItem.new {
            this.list = list
            this.name = name
            this.quantity = quantity
            this.unit = unit
            this.barcode = null
            this.isChecked = false
            this.checkedBy = null
            this.createdBy = null
            this.createdAt = now
            this.updatedAt = now
        }
    }
```

**Important:** Check if `createdBy` is nullable in the `ListItems` table definition. If not, we'll need to make it nullable or use a different approach. Look at `ListItems` table — if `createdBy` is non-nullable, use the list owner's account ID instead by looking it up from the list:

```kotlin
fun createForSharedAccess(
    listId: UUID,
    name: String,
    quantity: Double,
    unit: String?,
): ListItem =
    transaction(db) {
        val list = ShoppingList.findById(listId)
            ?: throw IllegalArgumentException("List not found: $listId")
        val now = Instant.now()

        ListItem.new {
            this.list = list
            this.name = name
            this.quantity = quantity
            this.unit = unit
            this.barcode = null
            this.isChecked = false
            this.checkedBy = null
            this.createdBy = list.owner
            this.createdAt = now
            this.updatedAt = now
        }
    }
```

Use whichever approach compiles. The implementer should check `ListItems.createdBy` nullability.

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "*SharedAccessRoutesTest*" -q`
Expected: All 13 tests PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt \
       backend/src/main/kotlin/no/shoppinglist/service/ListItemService.kt \
       backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt
git commit -m "feat: add shared list WRITE endpoints (add, edit, delete, clear checked)"
```

---

### Task 3: Backend — Change Expiry from Days to Hours

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/shoppinglist/Models.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/ListShareService.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/shoppinglist/ListSharing.kt`
- Test: `backend/src/test/kotlin/no/shoppinglist/service/ListShareServiceTest.kt`

**Step 1: Write failing test for hour-based expiry**

Add to `ListShareServiceTest.kt`:

```kotlin
test("createLinkShare with expirationHours sets correct expiry") {
    val share = listShareService.createLinkShare(
        listId = list.id.value,
        permission = SharePermission.READ,
        expirationHours = 6,
    )
    transaction {
        val expiresAt = share.expiresAt!!
        val now = Instant.now()
        // Should expire within 6 hours ± 1 minute
        val diffHours = java.time.Duration.between(now, expiresAt).toHours()
        diffHours shouldBe 6L
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*ListShareServiceTest*" -q`
Expected: FAIL — `createLinkShare` doesn't accept `expirationHours`

**Step 3: Implement the change**

In `Models.kt`, change `CreateShareRequest`:
```kotlin
@Serializable
data class CreateShareRequest(
    val type: String,
    val accountId: String? = null,
    val permission: String,
    val expirationHours: Int = 24,
)
```

In `ListShareService.kt`, change `createLinkShare`:
```kotlin
fun createLinkShare(
    listId: UUID,
    permission: SharePermission,
    expirationHours: Int,
): ListShare =
    transaction(db) {
        val list = ShoppingList.findById(listId)
            ?: throw IllegalArgumentException("List not found: $listId")

        ListShare.new {
            this.list = list
            this.type = ShareType.LINK
            this.account = null
            this.linkToken = generateToken()
            this.permission = permission
            this.expiresAt = Instant.now().plus(expirationHours.toLong(), ChronoUnit.HOURS)
            this.createdAt = Instant.now()
        }
    }
```

In `ListSharing.kt`, update the call site:
```kotlin
ShareType.LINK -> listShareService.createLinkShare(listId, permission, request.expirationHours)
```

Add validation in the `createShareRoute` — before creating:
```kotlin
if (shareType == ShareType.LINK && (request.expirationHours < 1 || request.expirationHours > 168)) {
    return@post call.respond(
        HttpStatusCode.BadRequest,
        mapOf("error" to "Expiration must be between 1 and 168 hours (7 days)"),
    )
}
```

**Step 4: Update all existing test calls**

All existing tests calling `createLinkShare` with `expirationDays` must be updated to use `expirationHours`. Search for `expirationDays` in the test directory and update:
- `expirationDays = 7` → `expirationHours = 168`
- `expirationDays = -1` → `expirationHours = -1` (for expired token tests)

**Step 5: Run all backend tests**

Run: `cd backend && ./gradlew test -q`
Expected: All tests PASS

**Step 6: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/shoppinglist/Models.kt \
       backend/src/main/kotlin/no/shoppinglist/service/ListShareService.kt \
       backend/src/main/kotlin/no/shoppinglist/routes/shoppinglist/ListSharing.kt \
       backend/src/test/
git commit -m "feat: change share link expiry from days to hours (1h-168h)"
```

---

### Task 4: Frontend — Refactor ShoppingListView to Accept Share Props

**Files:**
- Modify: `web/src/components/shopping-list/ShoppingListView.tsx`

**Step 1: Add shareToken and permission props**

Change the interface and component signature:

```typescript
type SharePermission = 'READ' | 'CHECK' | 'WRITE';

interface ShoppingListViewProps {
  listId: string;
  listName?: string;
  items: ListItem[];
  permission?: SharePermission;
  shareToken?: string;
  onBack: () => void;
  onShareClick?: () => void;
  onPinToggle?: () => void;
  onItemsChange?: (items: ListItem[]) => void;
}
```

The key insight: when `shareToken` is provided, the component must:
1. Use `fetch` with `/api/shared/{token}/...` URLs instead of `apiFetch` with `/lists/{id}/...`
2. Not use Redux dispatch (items managed by parent via `onItemsChange`)
3. Not render owner-only UI (share button, pin, comments)

**Step 2: Create an API helper for dual routing**

At the top of `ShoppingListView.tsx`, add a helper:

```typescript
function sharedFetch(shareToken: string, path: string, options?: RequestInit) {
  return fetch(`${API_BASE}/shared/${shareToken}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
}
```

**Step 3: Conditionalize all API calls and rendering**

For each handler (`handleAddItem`, `handleToggleCheck`, `handleRemoveItem`, `handleQuantityChange`, `handleClearChecked`, `handleRefresh`):
- If `shareToken` is present, use `sharedFetch(shareToken, '/items/...')`
- If `shareToken` is absent, use `apiFetch('/lists/${listId}/items/...')` (existing behavior)

For state management:
- If `shareToken` is present, call `onItemsChange` instead of Redux dispatch
- If absent, use Redux dispatch as before

For rendering:
- Hide add form if permission is READ or CHECK
- Hide delete buttons and quantity steppers if permission is READ or CHECK
- Hide clear-checked button if permission is not WRITE
- Disable checkboxes if permission is READ
- Hide share button, pin toggle, comments, back link if `shareToken` is present
- Hide keyboard shortcuts for delete/quantity if permission is not WRITE

Pass `disabled={permission === 'READ'}` to `ListItemRow` components.
For CHECK permission: pass `disabled={false}` but `onDelete={undefined}` and `onQuantityChange={undefined}`.

**Step 4: Run existing frontend tests to verify nothing is broken**

Run: `cd web && npm run test:run`
Expected: All existing tests PASS (default permission is WRITE, no shareToken = existing behavior)

**Step 5: Commit**

```bash
git add web/src/components/shopping-list/ShoppingListView.tsx
git commit -m "refactor: add shareToken and permission props to ShoppingListView"
```

---

### Task 5: Frontend — Rewrite SharedListView as Thin Wrapper

**Files:**
- Modify: `web/src/components/shared/SharedListView.tsx`
- Modify: `web/src/components/shared/SharedListView.test.tsx`

**Step 1: Rewrite SharedListView**

Replace the entire component. It becomes a thin wrapper that:
1. Fetches list data via `GET /api/shared/{token}`
2. Manages items in local state
3. Renders loading/expired/error states
4. Renders `ShoppingListView` with `shareToken`, `permission`, items, and `onItemsChange`

```typescript
import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { LoadingSpinner, ErrorAlert, Badge } from '../common';
import { API_BASE } from '../../services/api';
import ShoppingListView from '../shopping-list/ShoppingListView';
import { type ListItem } from '../../store/listsSlice';

interface SharedListViewProps {
  token: string;
}

interface SharedListData {
  id: string;
  name: string;
  permission: 'READ' | 'CHECK' | 'WRITE';
  items: ListItem[];
}

type ViewState = 'loading' | 'expired' | 'not_found' | 'success' | 'error';

export default function SharedListView({ token }: SharedListViewProps) {
  const { t } = useTranslation();
  const [viewState, setViewState] = useState<ViewState>('loading');
  const [listData, setListData] = useState<SharedListData | null>(null);
  const [items, setItems] = useState<ListItem[]>([]);

  useEffect(() => {
    const fetchList = async () => {
      try {
        const response = await fetch(`${API_BASE}/shared/${token}`);
        if (!response.ok) {
          if (response.status === 410) { setViewState('expired'); return; }
          if (response.status === 404) { setViewState('not_found'); return; }
          setViewState('error'); return;
        }
        const data = await response.json();
        setListData(data);
        setItems(data.items);
        setViewState('success');
      } catch {
        setViewState('error');
      }
    };
    fetchList();
  }, [token]);

  const handleItemsChange = useCallback((newItems: ListItem[]) => {
    setItems(newItems);
  }, []);

  if (viewState === 'loading') {
    return <div className="flex items-center justify-center p-8"><LoadingSpinner /></div>;
  }
  if (viewState === 'expired') {
    return <ErrorAlert variant="warning" title={t('shared.linkExpiredTitle')} message={t('shared.linkExpiredMessage')} />;
  }
  if (viewState === 'not_found') {
    return <ErrorAlert title={t('shared.notFoundTitle')} message={t('shared.notFoundMessage')} />;
  }
  if (viewState === 'error' || !listData) {
    return <ErrorAlert title={t('shared.errorTitle')} message={t('shared.errorMessage')} />;
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{listData.name}</h2>
        <Badge>{t(`shared.permission${listData.permission.charAt(0) + listData.permission.slice(1).toLowerCase()}`)}</Badge>
      </div>
      <ShoppingListView
        listId={listData.id}
        listName={listData.name}
        items={items}
        permission={listData.permission}
        shareToken={token}
        onBack={() => {}}
        onItemsChange={handleItemsChange}
      />
    </div>
  );
}
```

**Step 2: Update SharedListView tests**

Rewrite `SharedListView.test.tsx` to test:
- Loading state
- Expired (410) state
- Not found (404) state
- Success renders ShoppingListView with correct props
- Permission badge displays correctly

The tests should mock `fetch` and verify the wrapper behavior. Since `ShoppingListView` is now a child, we can mock it or test through it.

**Step 3: Run frontend tests**

Run: `cd web && npm run test:run`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add web/src/components/shared/SharedListView.tsx \
       web/src/components/shared/SharedListView.test.tsx
git commit -m "refactor: simplify SharedListView to thin wrapper around ShoppingListView"
```

---

### Task 6: Frontend — LinkShareTab Expiry Presets

**Files:**
- Modify: `web/src/components/shopping-list/share/LinkShareTab.tsx`

**Step 1: Replace expiry input with preset buttons**

```typescript
const EXPIRY_PRESETS = [
  { label: '1h', hours: 1 },
  { label: '6h', hours: 6 },
  { label: '24h', hours: 24 },
  { label: '3d', hours: 72 },
  { label: '7d', hours: 168 },
];

// Replace expirationDays state with:
const [expirationHours, setExpirationHours] = useState(24);

// Replace the expiration input JSX with:
<div>
  <label className="block text-sm font-medium leading-6 text-gray-900">
    {t('linkShare.expiresIn')}
  </label>
  <div className="mt-2 flex gap-2">
    {EXPIRY_PRESETS.map((preset) => (
      <button
        key={preset.hours}
        type="button"
        onClick={() => setExpirationHours(preset.hours)}
        className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
          expirationHours === preset.hours
            ? 'bg-indigo-600 text-white'
            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
        }`}
      >
        {preset.label}
      </button>
    ))}
  </div>
</div>
```

**Step 2: Update the API call**

Change the request body from `expirationDays` to `expirationHours`:
```typescript
body: JSON.stringify({
  type: 'LINK',
  permission,
  expirationHours,
}),
```

**Step 3: Add/update the i18n key**

Add `linkShare.expiresIn` to the translation files (check existing `linkShare.expiresInDays` and rename it).

**Step 4: Run frontend tests**

Run: `cd web && npm run test:run`
Expected: All tests PASS

**Step 5: Commit**

```bash
git add web/src/components/shopping-list/share/LinkShareTab.tsx
git commit -m "feat: replace expiry days input with hour preset buttons (1h-7d)"
```

---

### Task 7: Run Full Test Suite + Lint

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test -q`
Expected: All tests PASS

**Step 2: Run all frontend tests**

Run: `cd web && npm run test:run`
Expected: All tests PASS

**Step 3: Run linters**

Run: `make lint`
Expected: No lint errors (fix any that appear)

**Step 4: Final commit if any lint fixes were needed**

```bash
git add -A
git commit -m "fix: lint fixes for shared list full access"
```
