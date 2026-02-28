# External API & Embeddable Widget Design

## Overview

Expose the Shopping List app as a platform that external apps can integrate with. Two components:
1. A public REST API for creating and managing shopping lists from external apps
2. An embeddable Web Component (`<shopping-list-widget>`) with Shadow DOM CSS isolation

The first consumer is the Meet app, but the design is generic — any app can use it.

## External API

### Endpoints

**POST /api/external/lists**
Creates a new shopping list. Public, rate-limited (10 creates/minute per IP).

Request:
```json
{
  "title": "Party supplies",
  "email": "bob@example.com",
  "items": [
    { "name": "Chips", "quantity": 3, "unit": "bags" }
  ]
}
```

- `title` (required) — list name
- `email` (optional) — associate with an existing or future account
- `items` (optional) — pre-populate the list

Response:
```json
{
  "listId": "uuid",
  "shareToken": "abc123",
  "widgetUrl": "https://shopping-list.example.com/widget.js"
}
```

The returned `shareToken` grants full read/write access to the list. Existing shared access endpoints (`/api/shared/{token}/items`, etc.) work with this token.

### Email Association

- If an account with the given email exists: attach the list to that account immediately
- If no account exists: store the email as `pending_email` on the list
- On user registration/login, query for lists with matching `pending_email`, attach them to the new account, and clear the field
- Show a "You have X new shopping lists" notification on first login after attachment

### Rate Limiting

IP-based rate limiting via a Ktor plugin. No API keys required. Limits:
- 10 list creates per minute per IP
- 60 reads per minute per IP

### CORS

The external API and widget script must serve appropriate CORS headers to allow cross-origin requests from any domain.

## Embeddable Web Component

### Architecture

- Separate Vite build entry point: `web/src/widget/index.tsx`
- Produces a single JS bundle: `dist/widget.js` (target ~50-80KB gzipped)
- Registers a `<shopping-list-widget>` custom element
- Uses Shadow DOM for full CSS isolation — no styles leak in or out
- Tailwind CSS bundled inline within the shadow root (purged to widget-used classes only)
- Renders a React app inside the shadow root

### Widget API

```html
<shopping-list-widget
  token="abc123"
  api-url="https://shopping-list.example.com"
  theme="light"
></shopping-list-widget>
```

Attributes:
- `token` (required) — share token from the external API
- `api-url` (required) — base URL of the Shopping List backend
- `theme` (optional) — `light` or `dark`, defaults to `light`

### Widget UI

Reuses a slimmed-down version of the existing `SharedListView`:
- List title and items with checkboxes
- Add item form
- Real-time updates via WebSocket (connects using share token)
- Compact layout, no navigation chrome

### Build

- Separate Vite config (`vite.widget.config.ts`) with library mode output
- Self-contained bundle — no external dependencies leaked to the host page
- Served from Shopping List's domain with CORS headers

## Database Changes

New nullable column on the `shopping_lists` table:
- `pending_email: VARCHAR(255)` — email for future account association

New index: `idx_shopping_lists_pending_email ON shopping_lists(pending_email) WHERE pending_email IS NOT NULL`

## Testing

- Integration tests for `/api/external/lists` (create, rate limiting, email association)
- Test email claim flow: create list with email -> register account -> verify list attached on login
- CORS header validation tests
- Unit tests for Web Component lifecycle (mount, attribute changes, disconnect)
- Widget React component tests via Vitest + Testing Library
- Manual test: load widget on standalone HTML page to verify Shadow DOM isolation
