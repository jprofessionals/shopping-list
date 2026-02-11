# Mobile: Recurring Items & Local Notifications Design

## Overview

Two features for the Android app:
1. **Recurring items management** — full CRUD + pause/resume, matching the web frontend
2. **WebSocket-based local notifications** — shown when the app is backgrounded, with per-type toggles in settings

> **TODO**: Replace WebSocket-based notifications with a proper push solution (FCM or UnifiedPush) in the future. Current approach only works while the WebSocket connection is alive.

## Feature 1: Recurring Items

### Shared Module — DTOs & API Route

**New file: `shared/src/commonMain/kotlin/api/dto/RecurringItemDto.kt`**

DTOs matching the backend API:
- `RecurringItemResponse` — id, name, quantity, unit, frequency, lastPurchased, isActive, pausedUntil, createdBy (nested `CreatorResponse`)
- `CreateRecurringItemRequest` — name, quantity, unit, frequency
- `UpdateRecurringItemRequest` — name, quantity, unit, frequency
- `PauseRecurringItemRequest` — until (nullable date string)

**New file: `shared/src/commonMain/kotlin/api/routes/RecurringItemApi.kt`**

Follows the same pattern as `CommentApi.kt` — injected `ApiClient`, methods for each endpoint:
- `getItems(householdId)` → GET `/api/households/{id}/recurring-items`
- `createItem(householdId, request)` → POST
- `updateItem(householdId, itemId, request)` → PATCH
- `deleteItem(householdId, itemId)` → DELETE
- `pauseItem(householdId, itemId, request)` → POST `.../pause`
- `resumeItem(householdId, itemId)` → POST `.../resume`

### Shared Module — SQLDelight & Repository

**New file: `shared/src/commonMain/sqldelight/cache/RecurringItem.sq`**

Local cache table:
- `RecurringItemEntity`: id, householdId, name, quantity (Double), unit, frequency, lastPurchased, isActive (Boolean), pausedUntil, createdById, createdByName
- Queries: selectByHouseholdId, selectById, insertOrReplace, deleteById, deleteByHouseholdId, deleteAll

**New file: `shared/src/commonMain/kotlin/repository/RecurringItemRepository.kt`**

Follows `ListRepository` pattern:
- State: `getByHousehold(householdId): Flow<List<RecurringItemEntity>>`
- Online operations: create, update, delete, pause, resume — each calls API then upserts local cache
- Offline: Queues mutations to `SyncQueue` with entity type `RECURRING_ITEM` and operation types CREATE, UPDATE, DELETE, PAUSE, RESUME
- On network error: Creates temp entity locally, queues for sync

**SyncManager changes**: Add `RECURRING_ITEM` entity type handling. Operations map to the recurring items API endpoints.

### Android App — ViewModel & UI

**New file: `androidApp/.../viewmodel/RecurringItemsViewModel.kt`**

- State: `RecurringItemsUiState` — items list, isLoading, error, dialog state (showCreateDialog, showEditDialog, showPauseDialog, showDeleteConfirm, selectedItem)
- Methods: loadItems(householdId), create, update, delete, pause, resume
- Observes `RecurringItemRepository.getByHousehold()` flow

**New file: `androidApp/.../ui/households/RecurringItemsSection.kt`**

Composable section embedded in `HouseholdDetailScreen`:
- Header row: "Faste varer" title + add button
- Item cards: name, quantity + unit, frequency label, status (active/paused with date), last purchased date
- Per-item actions: edit, pause/resume, delete
- Create/Edit dialog: name, quantity, unit, frequency dropdown (Daglig, Ukentlig, Annenhver uke, Månedlig)
- Pause dialog: "Pause indefinitely" vs "Pause until date" with date picker
- Delete confirmation dialog

**Integration**: `RecurringItemsSection(householdId)` added to `HouseholdDetailScreen` between members and comments sections.

**DI**: Register `RecurringItemApi`, `RecurringItemRepository`, `RecurringItemsViewModel` in `AppModule.kt`.

## Feature 2: WebSocket-Based Local Notifications

### Notification Types

1. **New shopping list created** — triggered by `list:created` WebSocket event
2. **Item added to a list** — triggered by `item:added` WebSocket event
3. **New comment** — triggered by `comment:added` WebSocket event

All notifications are **only shown for events from other users** (skip if actor.id == current user id).

### Shared Module Changes

- Add `ListCreated(list, actor)` to `WebSocketEvent` sealed class and handle in `WebSocketEventHandler` (insert list into local DB)
- New `NotificationEvent` sealed class with three types:
  - `NewList(listName, actorName)`
  - `ItemAdded(listName, itemName, actorName)`
  - `NewComment(targetName, authorName, text)`
- `WebSocketEventHandler` emits `NotificationEvent` via `SharedFlow`

### Android Layer

**New class: `AppNotificationManager`**
- Creates Android notification channels (one per type: lists, items, comments)
- Collects `NotificationEvent` from `WebSocketEventHandler`
- Checks if app is backgrounded via `ProcessLifecycleOwner`
- Checks user's notification preferences before showing
- Shows local notification with title/body and tap action (deep link to relevant screen)

Lifecycle: Started/stopped alongside WebSocket connection. No foreground service — notifications only arrive while the connection is alive.

## Feature 3: Notification Preferences

### Backend Changes

Extend existing `UserPreferencesTable`:
- Add `notify_new_list` BOOLEAN (default true)
- Add `notify_item_added` BOOLEAN (default true)
- Add `notify_new_comment` BOOLEAN (default true)

Update `PreferencesData`, `PreferencesResponse`, `UpdatePreferencesRequest` with new fields. No new endpoints — existing `GET/PATCH /preferences` handles it.

### Mobile Shared Changes

Update `PreferencesDto.kt`: add `notifyNewList`, `notifyItemAdded`, `notifyNewComment` to both response and request DTOs.

### Android SettingsScreen Changes

Add "Notifications" card below existing Preferences card:
- Three toggle switches: "New shopping lists", "Items added to lists", "New comments"
- Each toggle calls `SettingsViewModel.updatePreferences(...)` with the relevant field
- `AppNotificationManager` reads these preferences before showing notifications

### Web Frontend Changes

Update `SettingsPage.tsx` with same three notification toggles for parity.
