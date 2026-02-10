# Phase 5C: API Documentation

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete API documentation by serving AsyncAPI docs and linking from OpenAPI.

**Architecture:** Add route to serve AsyncAPI HTML viewer, update OpenAPI description to link to WebSocket docs.

**Tech Stack:** Kotlin/Ktor, AsyncAPI HTML viewer (static HTML with embedded spec).

---

## Task 1: Create AsyncAPI HTML Viewer

**Files:**
- Create: `backend/src/main/resources/asyncapi/index.html`

**Step 1: Create HTML file that renders AsyncAPI spec**

Create an HTML file using the AsyncAPI React component to render the spec:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Shopping List WebSocket API</title>
    <link rel="stylesheet" href="https://unpkg.com/@asyncapi/react-component@1.0.0-next.54/styles/default.min.css">
    <style>
        body { margin: 0; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    </style>
</head>
<body>
    <div id="asyncapi"></div>
    <script src="https://unpkg.com/@asyncapi/react-component@1.0.0-next.54/browser/standalone/index.js"></script>
    <script>
        AsyncApiStandalone.render({
            schema: {
                url: '/docs/websocket/spec'
            },
            config: {
                show: {
                    sidebar: true,
                    info: true,
                    servers: true,
                    operations: true,
                    messages: true,
                    schemas: true
                }
            }
        }, document.getElementById('asyncapi'));
    </script>
</body>
</html>
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add AsyncAPI HTML viewer"
```

---

## Task 2: Add AsyncAPI Documentation Route

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/AsyncApiRoutes.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Create route file**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.asyncApiRoutes() {
    route("/docs/websocket") {
        get {
            val html = this::class.java.classLoader
                .getResourceAsStream("asyncapi/index.html")
                ?.bufferedReader()?.readText()
                ?: return@get call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
            call.respondText(html, ContentType.Text.Html)
        }

        get("/spec") {
            val yaml = this::class.java.classLoader
                .getResourceAsStream("asyncapi/websocket.yaml")
                ?.bufferedReader()?.readText()
                ?: return@get call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
            call.respondText(yaml, ContentType.parse("application/x-yaml"))
        }
    }
}
```

**Step 2: Add route to Application.kt**

Add `asyncApiRoutes()` call in the routing configuration.

**Step 3: Run lint and commit**

```bash
git add -A && git commit -m "feat: add AsyncAPI documentation route"
```

---

## Task 3: Link from OpenAPI to WebSocket Docs

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Step 1: Update info section with link to WebSocket docs**

Add to the description in the info section:

```yaml
info:
  title: Shopping List API
  version: 1.0.0
  description: |
    REST API for the Shopping List application.

    ## Real-time Updates

    For real-time synchronization, connect to the WebSocket API.
    See [WebSocket API Documentation](/docs/websocket) for details on events and subscriptions.
```

**Step 2: Add externalDocs section**

```yaml
externalDocs:
  description: WebSocket API Documentation
  url: /docs/websocket
```

**Step 3: Commit**

```bash
git add -A && git commit -m "docs: link OpenAPI to WebSocket documentation"
```

---

## Task 4: Run Tests and Lint

**Files:** None (verification only)

**Step 1: Run all tests**

Run: `make test`
Expected: All tests pass

**Step 2: Run lint**

Run: `make lint`
Expected: No errors

**Step 3: Fix any issues and commit**

---

## Summary

After completing all tasks, Phase 5C delivers:
- AsyncAPI documentation served at `/docs/websocket`
- AsyncAPI spec available at `/docs/websocket/spec`
- OpenAPI documentation links to WebSocket docs
- Complete API documentation for both REST and WebSocket APIs
