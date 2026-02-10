# Phase 4: Shopping Lists Design

## Overview

Shopping lists with items and sharing capabilities. Users can create lists, add items, and share with others via direct user invites or expiring links.

## Data Model & Access Rules

**Shopping Lists** use the existing domain model:
- `owner` — creator, always has full access
- `household` — optional, if set, all household members can access (unless `isPersonal=true`)
- `isPersonal` — when true, only owner sees it even within a household

**List Items** belong to a list and track:
- `name`, `quantity`, `unit` for the item details
- `isChecked`, `checkedBy` for completion state
- `createdBy`, `createdAt`, `updatedAt` for auditing

**List Shares** enables explicit sharing:
- `type`: `USER` or `LINK`
- For `USER`: `accountId` references who can access, no expiration
- For `LINK`: `linkToken` (random string), `expiresAt` (default 7 days from creation)
- `permission`: `READ` (view only), `CHECK` (can check/uncheck items), `WRITE` (full edit)

**Access Resolution** (checked in order):
1. Owner → full access
2. Household member + not personal → full access
3. User share → permission level from share
4. Link share (valid token, not expired) → permission level from share
5. Otherwise → 403 Forbidden

## REST API Endpoints

### Shopping Lists

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/lists` | All lists user can access (owned, household, shared) |
| POST | `/lists` | Create list (optionally attach to household) |
| GET | `/lists/:id` | Get list with items |
| PATCH | `/lists/:id` | Update name, isPersonal flag |
| DELETE | `/lists/:id` | Delete list (owner only) |

### List Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/lists/:id/items` | Add item (requires WRITE) |
| PATCH | `/lists/:id/items/:itemId` | Update item (requires WRITE) |
| DELETE | `/lists/:id/items/:itemId` | Remove item (requires WRITE) |
| POST | `/lists/:id/items/:itemId/check` | Toggle checked (requires CHECK or WRITE) |

### Sharing

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/lists/:id/shares` | List all shares (owner only) |
| POST | `/lists/:id/shares` | Create share (user or link) |
| PATCH | `/lists/:id/shares/:shareId` | Update permission or expiration |
| DELETE | `/lists/:id/shares/:shareId` | Revoke share |
| GET | `/shared/:token` | Access list via link token (public, unauthenticated) |

## Frontend Components & State

### Redux Slices

- `listsSlice` — stores lists with their items
- `sharesSlice` — stores shares for the currently viewed list

### Components

**List Management:**
- `ShoppingListsPage` — shows all accessible lists, grouped by household/personal
- `CreateListModal` — form to create list (name, optional household selection)
- `ShoppingListView` — main view showing list items with add/check/edit/delete

**Items:**
- `ListItemRow` — single item with checkbox, name, quantity, unit, edit/delete buttons
- `AddItemForm` — inline form at bottom of list to add items quickly
- `EditItemModal` — modal for editing item details

**Sharing:**
- `ShareListModal` — create shares (tabs for "User" and "Link")
- `ShareUserForm` — email input + permission dropdown
- `ShareLinkForm` — permission dropdown + expiration date picker (default 7 days)
- `SharesList` — shows existing shares with revoke buttons

**Access via Link:**
- `SharedListView` — standalone page at `/shared/:token` for anonymous link access
- Shows list in read-only, check-only, or full mode based on permission
- No authentication required

## Error Handling & Edge Cases

### Permission Enforcement

- All item/list operations check permission level before executing
- 403 returned with clear message: "Read access only" or "Check access only"
- Expired link shares return 410 Gone with message "This link has expired"

### Link Token Security

- Tokens are 32-character random alphanumeric strings (URL-safe)
- Tokens are unique (indexed in database)
- Rate limiting on `/shared/:token` to prevent brute-force (10 requests/minute per IP)

### Deletion Cascades

- Delete list → deletes all items and shares
- Delete share → just removes that share
- Remove user from household → their user-shares to household lists remain (explicit shares are independent)

### Edge Cases

- User tries to share with themselves → 400 "Cannot share with yourself"
- User tries to share with someone already shared → 400 "Already shared with this user"
- Create list in household user doesn't belong to → 403 Forbidden
- Owner cannot be removed via share revocation (they're not a share, they own it)

### Validation

- List name: 1-255 characters, required
- Item name: 1-1000 characters, required (TEXT in database)
- Quantity: positive number, defaults to 1
- Unit: optional, max 50 characters
- Permission: must be READ, CHECK, or WRITE
- Expiration: must be in the future when creating/updating link shares

## Testing Strategy

### Backend (Kotest)

**Service Tests:**
- `ShoppingListServiceTest` — CRUD, access resolution, household association
- `ListItemServiceTest` — CRUD, check/uncheck with user tracking
- `ListShareServiceTest` — create/revoke shares, expiration logic, permission levels

**Route Tests:**
- `ShoppingListRoutesTest` — endpoints with auth, permission checks, 403/404 cases
- `ListItemRoutesTest` — item operations, permission enforcement
- `ListShareRoutesTest` — share management, owner-only restrictions
- `SharedAccessRoutesTest` — public `/shared/:token` endpoint, expired links

### Frontend (Vitest + React Testing Library)

**Slice Tests:**
- `listsSlice.test.ts` — reducers for list/item state
- `sharesSlice.test.ts` — reducers for share state

**Component Tests:**
- `ShoppingListView.test.tsx` — renders items, add/check/delete flows
- `ShareListModal.test.tsx` — user share form, link share form with expiration
- `SharedListView.test.tsx` — permission-based UI (read-only vs editable)

### Key Test Scenarios

- User with READ permission cannot check items
- User with CHECK permission can check but not add items
- Expired link returns 410
- Household member sees non-personal lists
