# Valkey Session Management & Multi-Instance Support

## Problem

The backend stores all session state in memory:
- WebSocket subscriptions in `ConcurrentHashMap`s in `WebSocketSessionManager`
- No server-side token revocation (logout is client-side only)
- Cannot scale to multiple instances (Kubernetes requires minimum 2 pods)

## Solution Overview

Introduce Valkey (Redis-compatible) for:
1. **Token blacklist** - immediate access token invalidation on logout
2. **WebSocket Pub/Sub** - broadcast events across pods
3. **Short-lived JWTs + refresh tokens** - reduce blacklist dependency

## Architecture

```
┌──────────┐  ┌──────────┐
│  Pod A   │  │  Pod B   │
│ Backend  │  │ Backend  │
│  :8080   │  │  :8080   │
└────┬─────┘  └────┬─────┘
     │              │
     │  ┌────────┐  │
     └──┤ Valkey ├──┘   Pub/Sub + Blacklist
        └────────┘
     │              │
     │ ┌──────────┐ │
     └─┤PostgreSQL├─┘   Refresh tokens + data
       └──────────┘
```

## 1. Infrastructure

### Valkey Service

Docker Compose addition:
```yaml
valkey:
  image: valkey/valkey:8-alpine
  ports:
    - "6379:6379"
  command: valkey-server --save "" --appendonly no
```

No persistence needed. All stored data is either short-lived (blacklist TTL) or reconstructible (Pub/Sub subscriptions rebuild on reconnect).

### Client Library: Lettuce

Asynchronous, non-blocking Redis client for JVM. Fits Ktor's coroutine model. Jedis is synchronous and would block coroutines.

### Configuration

`application.conf`:
```hocon
valkey {
    host = "localhost"
    host = ${?VALKEY_HOST}
    port = 6379
    port = ${?VALKEY_PORT}
    password = ""
    password = ${?VALKEY_PASSWORD}
}
```

### New Service: `ValkeyService`

Wraps Lettuce connection. Created at startup, closed on shutdown. Exposes suspend functions for get/set/delete/publish/subscribe. Injected into services that need it. Backed by an interface for testability.

## 2. Auth: Short JWT + Refresh Tokens

### Current Flow

- 24-hour JWT, no refresh mechanism
- Logout is client-side only (delete token)
- No server-side token invalidation

### New Flow

**Access token**: JWT, 15-minute expiry, includes `jti` (JWT ID) claim for blacklisting.

**Refresh token**: Opaque UUID, 30-day expiry, SHA-256 hashed in PostgreSQL.

### New Table: `RefreshTokens`

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| accountId | UUID | FK to Account |
| tokenHash | String | SHA-256 hash of token |
| expiresAt | Timestamp | Expiry (30 days) |
| createdAt | Timestamp | Creation time |

Expired tokens cleaned up by periodic job.

### Endpoints

**Login** (`POST /auth/login`, Google OAuth callback):
- Returns `accessToken` (15 min JWT) + `refreshToken` (30 day opaque UUID)

**Refresh** (`POST /auth/refresh`) - new endpoint:
- Accepts `refreshToken`
- Verifies against DB (hash match + not expired)
- Token rotation: deletes old, creates new refresh token
- Returns new `accessToken` + new `refreshToken`
- Invalid/missing refresh token returns 401 (forces re-login)

**Logout** (`POST /auth/logout`):
- Deletes refresh token from DB
- Adds access token `jti` to Valkey blacklist with TTL = remaining lifetime

### Frontend Changes

Axios interceptor that:
- Catches 401 on access token
- Calls `/auth/refresh` with stored refresh token
- Updates stored token pair
- Retries original request

## 3. Token Blacklist in Valkey

### Purpose

Invalidate access tokens immediately on logout. Since access tokens live max 15 minutes, the blacklist is small and self-cleaning.

### Key Structure

```
blacklist:{jti} → "1"
TTL = remaining token lifetime
```

`jti` is a UUID added to JWT claims at generation time.

### Check Logic

In Ktor's `authenticate("auth-jwt")` block:

1. Verify JWT signature and expiry (as today)
2. Extract `jti` from claims
3. Check `EXISTS blacklist:{jti}` in Valkey
4. If exists → 401 Unauthorized
5. If Valkey is down → **fail-open** (skip check). Token expires within 15 min anyway.

### On Logout

```
SET blacklist:{jti} "1" EX <seconds-until-expiry>
```

TTL calculated as `token.expiresAt - now()`. Valkey auto-deletes the key when the token would have expired anyway.

## 4. WebSocket Broadcasting via Valkey Pub/Sub

### Current Problem

`WebSocketSessionManager` broadcasts directly to in-memory sessions. Events on pod A never reach clients connected to pod B.

### Solution: Split Responsibilities

**`WebSocketSessionManager`** (kept, simplified) - local WebSocket connections only:
- Holds `ConcurrentHashMap` of connected sessions on *this* pod
- Receives events from Pub/Sub and sends to relevant local clients
- Subscribes to Valkey channels at startup

**`WebSocketBroadcastService`** (new) - responsible for publishing events:
- When an event occurs, publishes to Valkey
- Replaces direct calls to `WebSocketSessionManager.broadcast()`

### Channel Structure

```
ws:list:{listId}           → item and list events
ws:household:{householdId} → household events
```

### Flow Example: "Item Added"

1. `ListItemService` saves item to DB
2. Calls `WebSocketBroadcastService.publish("ws:list:{listId}", event)`
3. Valkey forwards to all pods subscribed to that channel
4. Each pod's `WebSocketSessionManager` receives event
5. Sends to local clients subscribed to that list

### Subscription Management

- When a client connects and subscribes to a list, the pod subscribes to `ws:list:{listId}` in Valkey (if not already subscribed)
- When the last local client unsubscribes from a list, the pod unsubscribes from the Valkey channel

## 5. Resilience & Error Handling

### Valkey Down Behavior

| Function | Behavior | Consequence |
|----------|----------|-------------|
| Token blacklist | Fail-open (skip check) | Logged-out token works max 15 min |
| Pub/Sub broadcasting | Events reach local clients only | Clients on other pods miss real-time updates, see changes on next REST call |
| Refresh token | Unaffected (PostgreSQL) | Auth works normally |

### Health Check

`/health` endpoint includes Valkey status. Kubernetes readiness probe can use this, but Valkey down should **not** take down the pod - only degrade functionality.

### Reconnect Logic

- Lettuce has built-in auto-reconnect
- On reconnect, pod re-subscribes to all active Pub/Sub channels
- Logs warning on connection loss, info on reconnect

### Startup Order

- Backend starts even if Valkey is not ready yet
- Valkey-dependent features activate when connection is established
- Prevents full app failure because Valkey is 2 seconds slow at startup

## 6. Testing

### Unit Tests

`ValkeyService` behind an interface so it can be mocked.

### Integration Tests

TestContainers with Valkey image (same pattern as PostgreSQL today). Tests cover:
- Token blacklist set/check/expiry
- Pub/Sub publish/subscribe
- Refresh token rotation
- Fail-open behavior when Valkey is unavailable

### E2E Tests

Valkey added to `docker-compose.e2e.yml`.

**Two backend instances** for cross-pod verification:

```yaml
backend-1:
  build: ./backend
  ports:
    - "8080:8080"
  environment:
    VALKEY_HOST: valkey
    DATABASE_URL: jdbc:postgresql://db:5432/shopping

backend-2:
  build: ./backend
  ports:
    - "8081:8080"
  environment:
    VALKEY_HOST: valkey
    DATABASE_URL: jdbc:postgresql://db:5432/shopping
```

**Cross-pod test scenarios:**

WebSocket broadcasting:
1. User A connects WebSocket to backend-1 (port 8080)
2. User B connects WebSocket to backend-2 (port 8081)
3. User A adds an item via REST on backend-1
4. Verify user B receives `item:added` event via WebSocket on backend-2

Token revocation:
1. User logs in, gets access token
2. Logs out via backend-1 (token blacklisted in Valkey)
3. Uses same token against backend-2
4. Verify 401 response

Playwright tests use `baseURL` parameter per context to target specific backend instance.
