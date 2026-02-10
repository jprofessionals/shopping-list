# Mobile App Design - Compose Multiplatform

**Goal:** Native mobile app for the shopping list project using Compose Multiplatform. Android first, with iOS support later.

**Scope:** Full feature parity with the web frontend - auth, households, shopping lists, items, sharing, comments, preferences, activity feed, and real-time sync via WebSocket.

---

## Project Structure

The mobile app lives at `mobile/` with two modules:

- **`mobile/shared/`** - Kotlin Multiplatform module containing the API client, DTOs, repositories, local database, and sync logic. Reused on iOS later.
- **`mobile/androidApp/`** - Android entry point with Compose UI, ViewModels, and platform integrations.

### Dependencies

- **Ktor Client** (CIO engine for Android, Darwin for iOS later) - HTTP + WebSocket
- **kotlinx.serialization** - JSON parsing
- **SQLDelight** - Multiplatform local database for offline cache and sync queue
- **Koin** - Lightweight multiplatform DI
- **Compose Multiplatform** - UI framework
- **AndroidX Lifecycle** - ViewModel + StateFlow integration

---

## Data Layer

### API Client & DTOs

In `shared/src/commonMain/kotlin/api/`:

- Ktor Client wrapper with JSON serialization, JWT token management, and automatic token refresh on 401.
- `api/dto/` - All `@Serializable` request/response data classes matching the backend API, used for both REST and WebSocket.
- `api/routes/` - Typed API functions grouped by feature: `AuthApi`, `ListApi`, `HouseholdApi`, `ShareApi`, `CommentApi`, `PreferencesApi`.

WebSocket client connects to `/ws?token=<jwt>`, handles subscribe/unsubscribe, deserializes events into shared types. Automatic reconnection with exponential backoff.

Token storage via `TokenStore` expect/actual interface - Android Keystore on Android.

### Local Database & Offline Sync

SQLDelight schemas:

- **Cache tables** mirror backend entities: `ShoppingList`, `ListItem`, `Household`, `HouseholdMember`. Populated from API responses, serve as UI source of truth.
- **`SyncQueue` table** - `id`, `type` (CREATE/UPDATE/DELETE/CHECK), `entityType`, `entityId`, `payload` (JSON), `createdAt`, `retryCount`.

Sync flow:

1. **Online**: Mutations hit API directly. On success, update local cache.
2. **Offline**: Mutations applied optimistically to local cache, enqueued in `SyncQueue`.
3. **Reconnection**: `SyncManager` drains queue in order. Server-wins on conflict (409/404) - local cache refreshed from server, user notified via snackbar ("Your change to '{item}' was overridden").
4. **Failed syncs**: Retry up to 3 times with backoff, then discard with notification.

`ConnectivityMonitor` (expect/actual - Android `ConnectivityManager`, iOS `NWPathMonitor`) triggers sync on reconnect.

---

## Architecture - MVVM + Repository

### Repositories (shared module)

Each repository exposes `Flow` from SQLDelight for reactive UI updates:

- **`ListRepository`** - Shopping lists and items.
- **`HouseholdRepository`** - Households and members.
- **`AuthRepository`** - Login/register/logout, token lifecycle.
- **`ShareRepository`** - List sharing (user and link shares).
- **`CommentRepository`** - Comments on lists and households.
- **`PreferencesRepository`** - User preferences.

### ViewModels (Android module)

Each screen gets a ViewModel exposing `StateFlow<UiState>`:

- `LoginViewModel`, `RegisterViewModel`
- `ListsViewModel` (dashboard)
- `ListDetailViewModel` (single list with items)
- `HouseholdViewModel`, `HouseholdDetailViewModel`
- `ShareViewModel`
- `CommentsViewModel`
- `SettingsViewModel`

### Real-time data flow

WebSocket events -> Repository (updates local DB) -> SQLDelight Flow -> ViewModel -> UI re-renders. ViewModels don't know about WebSocket.

---

## UI Screens & Navigation

Bottom navigation bar with three sections:

- **Lists** (home) - All accessible lists, pinned at top, item counts. Pull-to-refresh. FAB to create.
- **Households** - Household list with member counts.
- **Settings** - Preferences, account info, logout.

### Key screens

- **Login/Register** - Forms + Google OAuth via Chrome Custom Tab, deep link back.
- **List Detail** - Items with checkboxes, swipe-to-delete, add input with autocomplete suggestions. Activity feed and comments via tabs. Real-time updates live.
- **Share Sheet** - Bottom sheet for managing shares. Android share intent for link distribution.
- **Household Detail** - Members with roles, add/remove.
- **Shared List View** - Deep link from share token, no auth, read/check only.

**`ConnectionStatusBanner`** at top when offline/reconnecting. Sync conflict notifications as snackbars.

---

## Testing

- **Shared unit tests** (`commonTest`, runs on JVM) - Repository logic, sync queue, API parsing, WebSocket events. Ktor `MockEngine` + SQLDelight in-memory driver.
- **Android ViewModel tests** (JVM + Robolectric) - StateFlow assertions with Turbine. Navigation tests.
- **Compose UI tests** - Critical flows: login, add item, check item, pull-to-refresh. Using `createComposeRule`.

No mobile E2E against real backend initially - existing Playwright tests validate API contracts.
