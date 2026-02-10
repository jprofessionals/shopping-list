

# Shopping List Application Design

## Overview

A shared household shopping list application with real-time sync across web and mobile platforms.

**Platforms:**
- Web: React + Redux (TypeScript)
- Android: Native Kotlin
- Backend: Kotlin (Ktor or Spring)

**Key Features:**
- Google authentication
- Households with shared lists
- Real-time sync via WebSockets
- Offline support (mobile)
- Recurring items
- Flexible sharing (user-to-user and links with permissions)

## High-Level Architecture

```
┌─────────────┐     ┌─────────────┐
│   React     │     │   Kotlin    │
│   Web App   │     │ Android App │
└──────┬──────┘     └──────┬──────┘
       │                   │
       │ HTTPS + WebSocket │
       └─────────┬─────────┘
                 │
         ┌───────▼───────┐
         │    Kotlin     │
         │    Backend    │
         │  (Ktor/Spring)│
         └───────┬───────┘
                 │
         ┌───────▼───────┐
         │   Database    │
         │     (TBD)     │
         └───────────────┘
```

**Backend responsibilities:**
- Google OAuth authentication
- User and household management
- Shopping list CRUD operations
- Real-time sync via WebSockets
- Generate share links
- Recurring item scheduling
- Barcode lookup proxy (future ibnreg integration)

**Web app (React + Redux):**
- Lightweight browser interface
- List management and item checking
- Household and sharing management
- Redux for state + WebSocket sync

**Android app (Kotlin):**
- Native mobile experience
- Offline read + queued writes with local database
- Barcode scanner integration (future)
- Background sync when connectivity returns

**Communication:**
- REST API for CRUD operations and auth
- WebSocket connection for real-time updates

## Data Model

### Account

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| email | String | From Google, unique |
| displayName | String | User's display name |
| avatarUrl | String? | Profile picture URL |
| createdAt | Timestamp | Account creation time |

### Household

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| name | String | e.g., "Home", "Cabin" |
| createdAt | Timestamp | Creation time |

### HouseholdMembership

| Field | Type | Description |
|-------|------|-------------|
| accountId | UUID | FK to Account |
| householdId | UUID | FK to Household |
| role | Enum | OWNER or MEMBER |
| joinedAt | Timestamp | When user joined |

- Accounts can belong to multiple households
- Households can have multiple owners

### ShoppingList

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| householdId | UUID? | FK to Household (null for standalone) |
| ownerId | UUID | FK to Account (creator) |
| name | String | List name |
| isPersonal | Boolean | Hidden from household if true |
| createdAt | Timestamp | Creation time |

### ListShare

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| listId | UUID | FK to ShoppingList |
| type | Enum | USER or LINK |
| accountId | UUID? | FK to Account (for USER type) |
| linkToken | String? | Token for LINK type |
| permission | Enum | READ, CHECK, or WRITE |
| createdAt | Timestamp | Share creation time |

**Permission levels:**
- **READ** - View list and items only
- **CHECK** - Can check/uncheck items, but not add or edit
- **WRITE** - Full edit access (add, edit, remove items)

### ListItem

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| listId | UUID | FK to ShoppingList |
| name | String | Item name |
| quantity | Number | Amount |
| unit | String? | "liters", "kg", "stk", etc. |
| barcode | String? | For future scanning |
| isChecked | Boolean | Checked off or not |
| checkedByAccountId | UUID? | Who checked it |
| createdByAccountId | UUID | Who added it |
| createdAt | Timestamp | Creation time |
| updatedAt | Timestamp | Last modification |

### RecurringItem

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| listId | UUID | FK to ShoppingList |
| name | String | Item name |
| quantity | Number | Amount |
| unit | String? | Unit of measurement |
| frequency | Enum | DAILY, WEEKLY, BIWEEKLY, MONTHLY |
| nextOccurrence | Date | When to add next |
| isActive | Boolean | Paused or active |
| createdByAccountId | UUID | Creator |

**Recurring behavior:**
- Backend job runs daily, adds items where `nextOccurrence <= today`
- After adding, calculates next occurrence
- Generated items are regular ListItems
- Users can pause/resume or delete recurring rules

## Authentication

Google OAuth 2.0 flow:

```
┌────────────┐      ┌────────────┐      ┌────────────┐
│   Client   │      │  Backend   │      │  Google    │
└─────┬──────┘      └─────┬──────┘      └─────┬──────┘
      │ 1. Login click    │                   │
      │──────────────────►│                   │
      │ 2. Redirect URL   │                   │
      │◄──────────────────│                   │
      │ 3. User authenticates                 │
      │──────────────────────────────────────►│
      │ 4. Auth code callback                 │
      │◄──────────────────────────────────────│
      │ 5. Send code      │                   │
      │──────────────────►│                   │
      │                   │ 6. Exchange code  │
      │                   │──────────────────►│
      │                   │ 7. Tokens + email │
      │                   │◄──────────────────│
      │ 8. Session token  │                   │
      │◄──────────────────│                   │
```

- Backend exchanges Google auth code for tokens
- Creates account on first login
- Issues session token (JWT or opaque) for API/WebSocket auth
- Clients store session token securely (Keystore on Android, httpOnly cookie on web)
- Backend manages Google token refresh

## Real-Time Sync (WebSockets)

### Connection Model

Client connects → Authenticates → Subscribes to accessible lists

### Connection States (shown in UI)

| State | Indicator | Description |
|-------|-----------|-------------|
| CONNECTED | Green | Real-time, instant updates |
| IDLE | Yellow | Disconnected after timeout, tap to reconnect |
| OFFLINE | Red | No network connectivity |

### Idle Handling

- Disconnect WebSocket after ~5-10 minutes of inactivity
- UI shows "Paused - tap to sync" indicator
- Any user action reconnects automatically
- On reconnect: fetch changes since last sync, resume WebSocket
- Mobile: disconnect when app backgrounds, reconnect on foreground

### Events (Server → Client)

- `item:added` - New item on subscribed list
- `item:updated` - Item name/quantity changed
- `item:checked` - Item checked/unchecked (includes who)
- `item:removed` - Item deleted
- `list:updated` - List name or settings changed
- `list:shared` - New share added
- `member:joined` - Someone joined household

### Events (Client → Server)

Same operations as requests. Server validates permissions, persists, then broadcasts to other subscribers.

### Offline Handling (Android)

- Stores current list state in local SQLite
- Queues changes made offline with timestamps
- On reconnect: sends queued changes
- Conflict resolution: last-write-wins for simple fields, merge for item additions

## REST API

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /auth/google | Initiate OAuth flow |
| GET | /auth/google/callback | Handle OAuth callback |
| POST | /auth/logout | End session |
| GET | /auth/me | Current user info |

### Households

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /households | List user's households |
| POST | /households | Create household |
| GET | /households/:id | Get household + members |
| PATCH | /households/:id | Update household name |
| DELETE | /households/:id | Delete household (owners only) |
| POST | /households/:id/members | Invite member by email |
| DELETE | /households/:id/members/:accountId | Remove member |
| PATCH | /households/:id/members/:accountId | Change role |

### Shopping Lists

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /lists | All lists user can access |
| POST | /lists | Create list |
| GET | /lists/:id | Get list with items |
| PATCH | /lists/:id | Update list settings |
| DELETE | /lists/:id | Delete list |
| POST | /lists/:id/shares | Create share (user or link) |
| DELETE | /lists/:id/shares/:shareId | Revoke share |

### Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /lists/:id/items | Add item |
| PATCH | /lists/:id/items/:itemId | Update item |
| DELETE | /lists/:id/items/:itemId | Remove item |
| POST | /lists/:id/items/:itemId/check | Toggle checked |

### Recurring Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /lists/:id/recurring | List recurring rules |
| POST | /lists/:id/recurring | Create recurring item |
| PATCH | /lists/:id/recurring/:recurringId | Update rule |
| DELETE | /lists/:id/recurring/:recurringId | Delete rule |

## Error Handling & Edge Cases

### Conflict Resolution

| Scenario | Resolution |
|----------|------------|
| Item additions | Always merge (no conflict) |
| Item checked/unchecked | Last-write-wins by timestamp |
| Item edited simultaneously | Last-write-wins |
| Item deleted while editing | Delete wins, notify editor |

### Permission Edge Cases

- User removed from household while viewing: WebSocket notifies, UI shows "Access revoked"
- Share link revoked while in use: Next request returns 403, prompt to close
- Owner leaves household: Must transfer ownership first or delete

### Rate Limiting

- API: Standard rate limits per user (e.g., 100 req/min)
- WebSocket: Throttle rapid-fire events from single client
- Share links: Rate limit by IP to prevent abuse

### Configurable Limits

All limits stored in configuration (environment variables):

| Limit | Default | Config Key |
|-------|---------|------------|
| Households per user | 10 | LIMIT_HOUSEHOLDS_PER_USER |
| Members per household | 20 | LIMIT_MEMBERS_PER_HOUSEHOLD |
| Lists per household | 50 | LIMIT_LISTS_PER_HOUSEHOLD |
| Items per list | 200 | LIMIT_ITEMS_PER_LIST |
| Active share links per list | 10 | LIMIT_SHARES_PER_LIST |

## Technical Standards

### 12-Factor App (Backend)

| Factor | Implementation |
|--------|----------------|
| Codebase | One repo, tracked in Git |
| Dependencies | Explicitly declared (Gradle/Maven) |
| Config | Environment variables for all config |
| Backing services | DB, cache as attached resources |
| Build/release/run | Separate stages, immutable releases |
| Processes | Stateless, all state in database |
| Port binding | Self-contained, exports HTTP/WS on configured port |
| Concurrency | Scale horizontally via process model |
| Disposability | Fast startup, graceful shutdown (drain WebSockets) |
| Dev/prod parity | Same backing services, containerized |
| Logs | Stream to stdout, aggregated externally |
| Admin processes | Migrations as one-off commands |

### API Specification

- **REST API**: OpenAPI 3.x specification
- **WebSocket Events**: AsyncAPI specification
- Generated from code annotations or maintained spec-first

### Testing Strategy

| Layer | Unit Tests | Integration Tests | E2E Tests |
|-------|------------|-------------------|-----------|
| Backend (Kotlin) | Yes | Yes (DB, external APIs) | Yes |
| Web (React) | Yes | - | Yes |
| Android (Kotlin) | Yes | Yes (local DB, API mocks) | - |

### Linting & Code Quality

| Application | Linter | Formatter | Additional |
|-------------|--------|-----------|------------|
| Backend (Kotlin) | Detekt | ktlint | - |
| Web (React) | ESLint | Prettier | TypeScript strict mode |
| Android (Kotlin) | Detekt + Android Lint | ktlint | - |

**Enforcement:**
- Pre-commit hooks
- CI pipeline fails on lint errors
- Shared Kotlin config between backend and Android

## Future Extensions (Out of Scope)

These are explicitly not in the initial build, but the design accommodates them:

### Barcode Scanning (ibnreg)

- Add `GET /products/barcode/:code` endpoint
- Backend proxies to ibnreg API, caches results
- Android app uses CameraX for scanning
- Item model already has `barcode` field ready

### Categories

- Add Category entity linked to product database
- Items can have optional categoryId
- Lists sorted/grouped by category (matches store layout)

### Product Database

- Local cache of products from ibnreg
- User-contributed products
- Price tracking over time

### Push Notifications

- "List updated" when not in app
- "You were added to household"
- Per-list notification settings

### Chat / Comments

- Comments on individual shopping lists (e.g., "Got the big pack instead")
- Household-level chat channel for coordination
- WebSocket-based real-time delivery
- Optional notifications for new messages
