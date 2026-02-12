# Shared List Full Access Design

## Problem

When sharing a shopping list via link with CHECK or WRITE permission, recipients can only view the list. The backend has no mutation endpoints for shared access, and the frontend SharedListView is read-only.

## Permission Model

- **READ**: View list and items only
- **CHECK**: View + check/uncheck items (uncheck to correct mistakes)
- **WRITE**: Full parity with regular list members — add, edit, delete, check, clear checked, smart parsing, autocomplete, quantity steppers

Authorization is token-based only — no login required. Tokens expire between 1 hour and 7 days (configurable at share creation).

## Design

### Backend — Shared Mutation Endpoints

New endpoints in `SharedAccessRoutes.kt` under `/api/shared/{token}`:

**CHECK+ permission:**
- `POST /api/shared/{token}/items/{itemId}/check` — toggle checked status (body: `{ isChecked: bool }`)

**WRITE permission:**
- `POST /api/shared/{token}/items` — add item
- `PATCH /api/shared/{token}/items/{itemId}` — edit item (name, quantity, unit)
- `DELETE /api/shared/{token}/items/{itemId}` — delete item
- `DELETE /api/shared/{token}/items/checked` — clear all checked items

A `validateShareToken` helper extracts list ID + permission from token, checks expiry, and returns the context. Reuses existing `ListItemService` methods.

**Expiry change:** Replace `expirationDays: Int` with `expirationHours: Int` in create-share request. Backend validates range 1–168 (1 hour to 7 days). No DB migration needed — the column already stores a computed `expiresAt` timestamp.

### Frontend — Reuse ShoppingListView

`ShoppingListView` gains two optional props:
- `shareToken: string` — when present, API calls route through `/api/shared/{token}/...`
- `permission: 'READ' | 'CHECK' | 'WRITE'` — controls which UI elements render (defaults to WRITE for authenticated users)

Permission-based rendering:
- **READ**: Items displayed, all interactions disabled
- **CHECK**: Checkboxes enabled, no add/edit/delete, no clear-checked
- **WRITE**: Full UI

`SharedListView` becomes a thin wrapper: fetches data via `GET /api/shared/{token}`, handles loading/expired/error states, then renders `ShoppingListView` with props. State is managed locally (no Redux).

### Share Creation UI

`LinkShareTab` changes:
- Replace expiration days number input with preset buttons: **1h, 6h, 24h, 3d, 7d**
- Default: 24 hours
- Sends `expirationHours` instead of `expirationDays`

## Implementation Order

1. **Backend shared endpoints** — validateShareToken helper, check/uncheck, add/edit/delete/clear-checked, expirationHours change, tests
2. **Frontend ShoppingListView refactor** — shareToken + permission props, API abstraction for dual routing, conditional rendering, tests
3. **Frontend SharedListView integration** — simplify to thin wrapper, wire mutation callbacks through share token API
4. **Share creation UI** — preset hour buttons, update API call, tests

## Out of Scope

- WebSocket real-time sync for shared views
- Comments on shared views
- Undo for shared views
