# Comments for Shopping Lists and Households

## Overview

Add a flat, chronological comment feed to shopping lists and households for short coordination messages and contextual notes. Comments are real-time via WebSocket.

## Use Cases

- **Coordination**: "Can you grab the organic kind?", "I already bought the milk"
- **Notes/context**: "John is allergic to peanuts", "This list is for Saturday's BBQ"

## Data Model

Single polymorphic `Comments` table:

```
Comments:
  id: UUID (PK)
  targetType: ENUM('LIST', 'HOUSEHOLD')
  targetId: UUID
  authorId: FK → Accounts
  text: TEXT (max ~2000 chars)
  editedAt: TIMESTAMP nullable
  createdAt: TIMESTAMP

Index: (targetType, targetId, createdAt)
```

Cascade deletes: when a shopping list or household is deleted, its comments are removed.

## Backend

### CommentService

- `create(targetType, targetId, authorId, text)` — validates target exists and user has access
- `findByTarget(targetType, targetId, limit, offset)` — paginated, chronological (newest last)
- `update(commentId, accountId, text)` — author-only, sets `editedAt`
- `delete(commentId, accountId)` — author-only

### Access Control

- **List comments**: same permission check as the list (owner, household member, or share with at least READ)
- **Household comments**: must be a household member (any role)

### Routes

Nested under existing resources, both delegating to the same `CommentService`:

```
POST   /lists/{id}/comments            — add comment
GET    /lists/{id}/comments             — get comments (paginated)
PATCH  /lists/{id}/comments/{cid}       — edit comment
DELETE /lists/{id}/comments/{cid}       — delete comment

POST   /households/{id}/comments        — add comment
GET    /households/{id}/comments         — get comments (paginated)
PATCH  /households/{id}/comments/{cid}  — edit comment
DELETE /households/{id}/comments/{cid}  — delete comment
```

## Real-time WebSocket Events

### Event Types

```
CommentAddedEvent:
  type: "comment_added"
  targetType: LIST | HOUSEHOLD
  targetId: UUID
  comment: { id, text, author: { id, displayName, avatarUrl }, createdAt }

CommentUpdatedEvent:
  type: "comment_updated"
  targetType: LIST | HOUSEHOLD
  targetId: UUID
  commentId: UUID
  text: string
  editedAt: timestamp

CommentDeletedEvent:
  type: "comment_deleted"
  targetType: LIST | HOUSEHOLD
  targetId: UUID
  commentId: UUID
```

### Subscriptions

- **List comments**: piggyback on existing list subscriptions
- **Household comments**: new household subscriptions in `WebSocketSessionManager`, auto-subscribed on connect (same pattern as lists)

### Broadcast Flow

Route handler → `CommentService` → `eventBroadcaster.broadcastCommentAdded/Updated/Deleted()` → `sessionManager.broadcastToList()` or `broadcastToHousehold()` → all connected members receive the event (excluding the actor).

## Frontend

### Redux (`commentsSlice`)

```
State:
  commentsByTarget: { [key: string]: Comment[] }  // key = "LIST:<uuid>" or "HOUSEHOLD:<uuid>"
  loading: boolean

Async thunks:
  fetchComments(targetType, targetId)
  postComment(targetType, targetId, text)
  editComment(targetType, targetId, commentId, text)
  deleteComment(targetType, targetId, commentId)

WebSocket actions:
  addComment()
  updateComment()
  removeComment()
```

### WebSocket Bridge

Three new handlers in `websocketBridge.ts` dispatching into the comments slice. Skips own events (optimistic updates already applied).

### UI Component

Single `CommentFeed` component used in both `ShoppingListView` and household detail view:

- Scrollable list of comments with author avatar, name, timestamp, and "(edited)" indicator
- Text input at the bottom to post
- Inline edit (click to edit own comment) and delete (with confirmation)
- Auto-scrolls to bottom on new comments
- Loads initial comments via REST on mount, then real-time updates via WebSocket

## i18n

Add keys to all 5 language files:

- Labels: "Comments", "Add a comment...", "Edit", "Delete", "(edited)"
- Confirmations: "Delete this comment?"
- Errors: "Failed to post comment"

## Testing

### Backend (Kotest + TestContainers)

- `CommentServiceTest` — CRUD, access control (no access → rejected, can't edit other's comment), cascade delete
- Route tests — HTTP status codes, pagination, auth enforcement

### Frontend (Vitest + RTL)

- `commentsSlice.test.ts` — reducers and async thunks
- `CommentFeed.test.tsx` — rendering, posting, editing, deleting, WebSocket event handling

### E2E (Playwright)

- Post a comment on a list, verify it appears for another user in real time
- Edit and delete own comment
