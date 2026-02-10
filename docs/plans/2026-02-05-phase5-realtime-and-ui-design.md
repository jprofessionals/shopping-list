# Phase 5: Real-time Sync & UI Polish

## Overview

This phase establishes real-time synchronization infrastructure, adds backend support for enhanced UI features, and delivers a mobile-first UI overhaul with improved information density, editing workflows, and quality-of-life features.

## Sub-phases

| Sub-phase | Focus | Dependencies |
|-----------|-------|--------------|
| 5A | Real-time sync infrastructure | None |
| 5B | Backend for new UI features | 5A (activity tracking uses WebSocket events) |
| 5C | API documentation | 5A, 5B |
| 5D | UI migration & new features | 5A, 5B |

---

## Sub-phase 5A: Real-time Sync Infrastructure

### WebSocket Connection

**Endpoint:** `ws://localhost:8080/ws`

**Authentication:** JWT token passed as query parameter or in first message after connection.

```
ws://localhost:8080/ws?token=<jwt>
```

**Connection lifecycle:**
1. Client connects with JWT
2. Server validates token, associates connection with account
3. Server auto-subscribes client to all accessible lists (owned, shared, household)
4. Client receives events for subscribed lists
5. Client can manually subscribe/unsubscribe to specific lists

### Connection States

| State | Description | UI Indicator |
|-------|-------------|--------------|
| CONNECTING | Initial connection in progress | Spinner |
| CONNECTED | Active, receiving events | Green dot |
| RECONNECTING | Lost connection, attempting reconnect | Yellow dot + "Reconnecting..." |
| OFFLINE | No network connectivity | Red dot + "Offline" |

### Events (Server → Client)

**Item events:**
```json
{
  "type": "item:added",
  "listId": "uuid",
  "item": { "id": "uuid", "name": "Milk", "quantity": 2, "unit": "l", ... },
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:30:00Z"
}

{
  "type": "item:updated",
  "listId": "uuid",
  "item": { "id": "uuid", "name": "Milk", "quantity": 3, ... },
  "changes": ["quantity"],
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:31:00Z"
}

{
  "type": "item:checked",
  "listId": "uuid",
  "itemId": "uuid",
  "isChecked": true,
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:32:00Z"
}

{
  "type": "item:removed",
  "listId": "uuid",
  "itemId": "uuid",
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:33:00Z"
}
```

**List events:**
```json
{
  "type": "list:updated",
  "list": { "id": "uuid", "name": "Weekly Groceries", ... },
  "changes": ["name"],
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:34:00Z"
}

{
  "type": "list:created",
  "list": { "id": "uuid", "name": "New List", ... },
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:35:00Z"
}

{
  "type": "list:deleted",
  "listId": "uuid",
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:36:00Z"
}
```

**Share events:**
```json
{
  "type": "list:shared",
  "listId": "uuid",
  "share": { "id": "uuid", "type": "USER", "permission": "WRITE", ... },
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:37:00Z"
}

{
  "type": "list:unshared",
  "listId": "uuid",
  "shareId": "uuid",
  "actor": { "id": "uuid", "displayName": "Lars" },
  "timestamp": "2026-02-05T10:38:00Z"
}
```

**Subscription management:**
```json
{
  "type": "subscribed",
  "listIds": ["uuid1", "uuid2"]
}

{
  "type": "unsubscribed",
  "listIds": ["uuid1"]
}

{
  "type": "access:revoked",
  "listId": "uuid",
  "reason": "share_removed"
}
```

### Events (Client → Server)

**Subscribe/unsubscribe:**
```json
{
  "type": "subscribe",
  "listIds": ["uuid1", "uuid2"]
}

{
  "type": "unsubscribe",
  "listIds": ["uuid1"]
}
```

**Ping/pong for keepalive:**
```json
{ "type": "ping" }
{ "type": "pong" }
```

### Reconnection & Delta Sync

**On reconnect:**
1. Client sends last known event timestamp
2. Server sends all events since that timestamp (up to a limit)
3. If too many events or too old, server sends full state refresh

```json
// Client reconnect request
{
  "type": "reconnect",
  "lastEventTimestamp": "2026-02-05T10:30:00Z"
}

// Server response - incremental
{
  "type": "sync:incremental",
  "events": [ ... ]
}

// Server response - full refresh needed
{
  "type": "sync:full",
  "lists": [ ... ],
  "reason": "too_many_events"
}
```

### Idle Handling

- Disconnect WebSocket after 10 minutes of no user activity
- UI shows "Paused - tap to sync" indicator
- Any user interaction reconnects automatically
- Mobile: disconnect on app background, reconnect on foreground

### Backend Implementation

**New files:**
- `WebSocketRoutes.kt` - WebSocket endpoint setup
- `WebSocketSession.kt` - Session management, authentication
- `WebSocketEventBroadcaster.kt` - Broadcasts events to subscribed clients
- `EventStore.kt` - Stores recent events for delta sync (in-memory with TTL, or DB table)

**Modifications:**
- All service methods that modify data call `WebSocketEventBroadcaster.broadcast(event)`
- `ListItemService`, `ShoppingListService`, `ListShareService` gain event emission

### Future: Scalable Session Management

The current implementation uses in-memory `ConcurrentHashMap` for session tracking, which works for single-server deployments. For horizontal scaling (multiple backend instances), a distributed approach is needed.

**Recommended approach: Redis Pub/Sub**

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Server A   │     │  Server B   │     │  Server C   │
│ (local WS)  │     │ (local WS)  │     │ (local WS)  │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌──────▼──────┐
                    │    Redis    │
                    │   Pub/Sub   │
                    └─────────────┘
```

**How it works:**
1. Each server tracks only its own local WebSocket sessions
2. When an event needs broadcasting, publish to Redis channel (e.g., `list:{listId}:events`)
3. All servers subscribe to relevant Redis channels
4. Each server forwards received events to its local sessions

**Changes required:**
- `WebSocketSessionManager` → `LocalSessionManager` (tracks local sessions only)
- New `RedisEventPublisher` replaces direct broadcast calls
- New `RedisEventSubscriber` listens and forwards to local sessions
- Redis connection configuration in `application.conf`

**Benefits:**
- Stateless servers (any server can handle any request)
- Horizontal scaling without sticky sessions
- Built-in Redis clustering for high availability

**Timeline:** Future phase, after validating single-server implementation works correctly.

---

## Sub-phase 5B: Backend for New UI Features

### Enhanced List Response

Modify `GET /lists` to include summary data:

```json
{
  "lists": [
    {
      "id": "uuid",
      "name": "Weekly Groceries",
      "householdId": "uuid",
      "isPersonal": false,
      "isOwner": true,
      "itemCount": 8,
      "uncheckedCount": 3,
      "previewItems": ["Milk", "Bread", "Eggs"],
      "lastActivity": {
        "type": "item:checked",
        "actorName": "Lars",
        "itemName": "Butter",
        "timestamp": "2026-02-05T10:30:00Z"
      },
      "isPinned": true,
      "updatedAt": "2026-02-05T10:30:00Z"
    }
  ]
}
```

### Activity Tracking

**New table: `list_activity`**

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| list_id | UUID | FK to shopping_list |
| account_id | UUID | Who performed action |
| action_type | String | item_added, item_checked, etc. |
| target_name | String | Item name (for display) |
| created_at | Timestamp | When it happened |

**Retention:** Keep last 100 events per list, or 7 days, whichever is less.

**Endpoint:** `GET /lists/:id/activity?limit=20`

```json
{
  "activity": [
    {
      "type": "item_checked",
      "actorName": "Lars",
      "targetName": "Milk",
      "timestamp": "2026-02-05T10:30:00Z"
    }
  ]
}
```

### Pinned Lists

**New table: `pinned_list`**

| Column | Type | Description |
|--------|------|-------------|
| account_id | UUID | FK to account |
| list_id | UUID | FK to shopping_list |
| pinned_at | Timestamp | When pinned |

**Endpoints:**

```
POST /lists/:id/pin
DELETE /lists/:id/pin
```

### Item History & Autocomplete

**New table: `item_history`**

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| account_id | UUID | FK to account |
| name | String | Item name (normalized lowercase) |
| display_name | String | Original casing |
| typical_quantity | Int | Most common quantity used |
| typical_unit | String? | Most common unit used |
| use_count | Int | How many times added |
| last_used_at | Timestamp | For sorting by recency |

**Updated on every item add.** If item with same normalized name exists, update counts. Otherwise insert.

**Endpoint:** `GET /items/suggestions?q=pot&limit=10`

```json
{
  "suggestions": [
    {
      "name": "Potatoes",
      "typicalQuantity": 1,
      "typicalUnit": "kg",
      "useCount": 15
    },
    {
      "name": "Potato chips",
      "typicalQuantity": 2,
      "typicalUnit": null,
      "useCount": 3
    }
  ]
}
```

**Fuzzy matching:** Use PostgreSQL `similarity()` function (pg_trgm extension) or Levenshtein distance. Order by similarity score * use_count for relevance.

### User Preferences

**New table: `user_preferences`**

| Column | Type | Description |
|--------|------|-------------|
| account_id | UUID | FK to account (PK) |
| smart_parsing_enabled | Boolean | Default true |
| default_quantity | Int | Default 1 |
| theme | String | "system", "light", "dark" |
| updated_at | Timestamp | Last change |

**Endpoints:**

```
GET /preferences
PATCH /preferences
```

```json
{
  "smartParsingEnabled": true,
  "defaultQuantity": 1,
  "theme": "system"
}
```

### Bulk Operations

**Clear all checked items:**

```
DELETE /lists/:id/items/checked
```

Returns list of deleted item IDs (for undo support):

```json
{
  "deletedItemIds": ["uuid1", "uuid2", "uuid3"]
}
```

**Undo support:** Client stores deleted items locally for 30 seconds. Undo re-creates them via `POST /lists/:id/items/bulk`:

```
POST /lists/:id/items/bulk
```

```json
{
  "items": [
    { "name": "Milk", "quantity": 2, "unit": "l" },
    { "name": "Bread", "quantity": 1, "unit": null }
  ]
}
```

---

## Sub-phase 5C: API Documentation

### OpenAPI Updates

Add to existing OpenAPI spec:
- Enhanced list response schema with summary fields
- Activity endpoints
- Pin/unpin endpoints
- Item suggestions endpoint
- User preferences endpoints
- Bulk operations endpoints

### WebSocket Documentation

Create AsyncAPI 2.x specification (`asyncapi.yaml`) documenting:
- WebSocket connection URL and authentication
- All event types with schemas
- Subscribe/unsubscribe commands
- Reconnection protocol

**Serve alongside OpenAPI:** Add `/docs/websocket` route serving AsyncAPI documentation (using AsyncAPI Studio or similar viewer).

**Link from Swagger UI:** Add description in OpenAPI pointing to WebSocket docs for real-time features.

---

## Sub-phase 5D: UI Migration & New Features

### Phase 1: WebSocket Integration

**New files:**
- `src/services/websocket.ts` - Connection management, reconnection logic
- `src/store/websocketSlice.ts` - Connection state (connected/reconnecting/offline)

**Modifications:**
- Existing Redux slices listen to WebSocket events and update state
- Remove manual refresh calls after mutations (WebSocket broadcasts handle it)
- Add connection status indicator to header

### Phase 2: Navigation Overhaul

**Routing (`react-router-dom`):**
- `/` - Dashboard home
- `/lists` - All lists page
- `/lists/:id` - List detail
- `/households` - Households list
- `/households/:id` - Household detail
- `/settings` - User preferences

**Bottom navigation component:**
- Fixed to bottom on mobile (< 768px)
- Converts to sidebar on desktop (>= 768px)
- Five items: Home, Lists, Add (center), Households, Profile
- Add button is context-aware with "New list instead" option

### Phase 3: Dashboard Home

**Components:**
- `PinnedListsRow` - Horizontal scroll of pinned lists
- `NeedsAttentionList` - Lists with unchecked items, sorted by updatedAt
- `ListCard` - Rich card with counts, preview, activity

**Data flow:**
- Fetch lists on mount (already includes summary data from 5B)
- Filter for pinned and needs-attention views
- WebSocket updates automatically refresh cards

### Phase 4: List Cards

**ListCard component displays:**
- Name + badge (Private or household name)
- "3 remaining" or "All done"
- 2-3 item preview (truncated)
- Last activity line
- Expand on hover/long-press for 5 items + quick actions

### Phase 5: Editing Improvements

**ListItemRow enhancements:**
- Tap quantity → inline stepper (+/- buttons)
- Swipe left/right on quantity → increment/decrement
- Long-press quantity → direct number input
- Edit icon → opens modal for name/unit

**Quick-add input:**
- Single field at top of list
- Autocomplete dropdown with fuzzy matching
- Progressive smart parsing (preview shows parsed result)
- Settings toggle for smart parsing

### Phase 6: Quality of Life

**Clear all checked:**
- Button in checked section header
- Calls bulk delete endpoint
- Stores items locally for undo

**Undo toast:**
- Appears at bottom for 5 seconds after destructive actions
- "Deleted Milk · Undo" with tap to restore
- Uses bulk create endpoint for restore

**Keyboard shortcuts (desktop):**
- `/` or `n` - Focus quick-add
- `Enter` - Submit item
- `↑↓` - Navigate items
- `Space` - Toggle check
- `Delete` - Remove item
- `+/-` - Adjust quantity
- `?` - Show help

**Pull-to-refresh (mobile):**
- Pull down triggers full list refresh
- Visual feedback during refresh
- Less critical once WebSocket is working

### Phase 7: Settings Page

**Sections:**
- Account info (display name, email, sign out)
- Preferences (smart parsing, default quantity, theme)
- Pinned lists management
- Keyboard shortcuts reference

---

## Data Model Changes Summary

### New Tables

| Table | Purpose |
|-------|---------|
| list_activity | Activity feed storage |
| pinned_list | User's pinned lists |
| item_history | Autocomplete suggestions |
| user_preferences | User settings |
| websocket_event (optional) | Event store for delta sync |

### Modified Tables

| Table | Changes |
|-------|---------|
| account | None (preferences in separate table) |
| shopping_list | Add `updated_at` if not present |

---

## Not in This Phase

- Recurring items (Phase 6)
- Android app (Phase 7+)
- Barcode scanning
- Categories
- Push notifications
- Chat/comments
