# Recurring Items Design

## Overview

Recurring items allow household members to define products that should be purchased regularly. A background scheduler checks daily for items that are due, and automatically creates a new shopping list containing those items. Users can then add additional items manually to the generated list.

## Core Concepts

- **Recurring items** belong to a household (not a specific list)
- Each item has its own **frequency**: DAILY, WEEKLY, BIWEEKLY, or MONTHLY
- Items are included on new lists based on **how long since they were last purchased** (checked off)
- A **scheduler** runs at a configurable interval (daily in prod, milliseconds in E2E tests)
- A new list is created **only when at least one item is due**
- Users can **pause** recurring items indefinitely or until a specific date

## Database Changes

### Modified: `recurring_items` table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| household_id | FK -> Households | Owner household |
| name | VARCHAR(255) | Item name |
| quantity | DOUBLE | Amount |
| unit | VARCHAR(50)? | Unit of measurement |
| frequency | ENUM | DAILY, WEEKLY, BIWEEKLY, MONTHLY |
| last_purchased | DATE? | Last time the item was checked off (null = never) |
| is_active | BOOL | Active or paused |
| paused_until | DATE? | Auto-reactivation date (null = paused indefinitely when is_active=false) |
| created_by_id | FK -> Accounts | Creator |

Changes from existing schema:
- **Remove** `list_id` reference (items belong to household, not a list)
- **Add** `household_id` reference
- **Replace** `next_occurrence` with `last_purchased` (nullable)
- **Add** `paused_until` (nullable)

### Modified: `list_items` table

- **Add** `recurring_item_id` (nullable FK -> recurring_items) — links generated items back to their recurring source, so checking off updates `last_purchased`

## Configuration

Frequencies are defined in `application.conf` (HOCON) and overridable per environment:

```hocon
recurring {
    scheduler {
        interval = 1d
        initialDelay = 1m
    }
    frequencies {
        daily = 1d
        weekly = 7d
        biweekly = 14d
        monthly = 30d
    }
}
```

Test override example:
```hocon
recurring.scheduler.interval = 100ms
recurring.frequencies.daily = 100ms
recurring.frequencies.weekly = 500ms
```

The `RecurringFrequency` enum (DAILY, WEEKLY, etc.) serves as a key to look up the actual duration from config, keeping the domain model clean while intervals remain flexible.

The scheduler uses Kotlin coroutines with `delay()` — no external scheduling framework needed.

## Backend

### RecurringItemService

CRUD operations for recurring items:

- `getByHousehold(householdId)` — list all recurring items for a household
- `create(householdId, accountId, request)` — create a new recurring item
- `update(recurringItemId, request)` — update name, quantity, frequency, etc.
- `delete(recurringItemId)` — delete a recurring item
- `pause(recurringItemId, until: Date?)` — set isActive=false, optional pausedUntil
- `resume(recurringItemId)` — set isActive=true, clear pausedUntil
- `markPurchased(recurringItemId, date)` — update lastPurchased (called when item is checked off)

### RecurringScheduler

Coroutine-based background job:

1. Starts on app startup, runs at configured interval
2. For each household with active recurring items:
   a. Reactivate items where `pausedUntil` has passed
   b. Find due items: `lastPurchased + frequencyDuration <= now` (or `lastPurchased == null`)
   c. If due items exist: create a new `ShoppingList` via existing `ShoppingListService`
   d. Add due items as `ListItem` via existing `ListItemService`, with `recurringItemId` set
3. Log what was created for traceability

### Check-off Integration

In existing `ListItemService.checkItem()`: when an item with `recurringItemId` is checked off, call `RecurringItemService.markPurchased()` automatically.

### WebSocket Notification

When the scheduler creates a new list, it uses existing `EventBroadcaster.broadcastListCreated()` to send a `list:created` event via `broadcastToHousehold()`. A system actor is used so clients can distinguish auto-generated lists from user-created ones.

No new event type needed — existing infrastructure is reused.

## API Endpoints

All endpoints require household membership.

```
GET    /api/households/:id/recurring-items                    # List all recurring items
POST   /api/households/:id/recurring-items                    # Create recurring item
PATCH  /api/households/:id/recurring-items/:itemId            # Update item
DELETE /api/households/:id/recurring-items/:itemId            # Delete item
POST   /api/households/:id/recurring-items/:itemId/pause      # Pause (body: { until?: "2026-03-01" })
POST   /api/households/:id/recurring-items/:itemId/resume     # Resume
```

### Request/Response Models

```json
// POST/PATCH request
{
  "name": "Melk",
  "quantity": 1.0,
  "unit": "liter",
  "frequency": "WEEKLY"
}

// GET response (single item)
{
  "id": "uuid",
  "name": "Melk",
  "quantity": 1.0,
  "unit": "liter",
  "frequency": "WEEKLY",
  "lastPurchased": "2026-02-10",
  "isActive": true,
  "pausedUntil": null,
  "createdBy": { "id": "uuid", "displayName": "Lars" }
}
```

## Frontend

### "Faste varer" Page

Accessible from the household page. Displays all recurring items with:

- Item name, quantity, unit, frequency
- Status (active / paused, with "paused until March 1" if applicable)
- Last purchased date
- Actions: edit, pause/resume, delete

### Create/Edit

Modal or inline form with fields for name, quantity, unit, and frequency dropdown (Daglig, Ukentlig, Annenhver uke, Månedlig).

### Pause Dialog

User chooses between "Pause indefinitely" or "Pause until date" with a date picker.

### Toast Notification

When a `list:created` event arrives via WebSocket for an auto-generated list, show a toast: "Ny handleliste opprettet med X varer".

### Redux

New `recurringItemsSlice` with standard CRUD operations + pause/resume. State is fetched per household.

### Recurring Item Badge

Items on a shopping list that originate from a recurring item display a small repeat icon/badge, so the user can see it was auto-generated.
