package no.shoppinglist.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import no.shoppinglist.config.JwtConfig
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.service.TokenBlacklistService
import no.shoppinglist.websocket.ErrorEvent
import no.shoppinglist.websocket.PongEvent
import no.shoppinglist.websocket.SubscribeCommand
import no.shoppinglist.websocket.SubscribedEvent
import no.shoppinglist.websocket.UnsubscribeCommand
import no.shoppinglist.websocket.UnsubscribedEvent
import no.shoppinglist.websocket.WebSocketBroadcastService
import no.shoppinglist.websocket.WebSocketSessionManager
import java.util.UUID

private val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

fun Route.webSocketRoutes(
    jwtConfig: JwtConfig,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
    householdService: HouseholdService,
    tokenBlacklistService: TokenBlacklistService,
    broadcastService: WebSocketBroadcastService,
) {
    webSocket("/ws") {
        val accountId =
            authenticateWebSocket(jwtConfig, tokenBlacklistService)
                ?: return@webSocket

        sessionManager.addSession(accountId, this)
        autoSubscribe(accountId, sessionManager, shoppingListService, householdService, broadcastService)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    handleCommand(frame.readText(), accountId, sessionManager, shoppingListService, broadcastService)
                }
            }
        } finally {
            sessionManager.removeSession(accountId, this)
        }
    }
}

@Suppress("ReturnCount")
private suspend fun DefaultWebSocketServerSession.authenticateWebSocket(
    jwtConfig: JwtConfig,
    tokenBlacklistService: TokenBlacklistService,
): UUID? {
    val token = call.request.queryParameters["token"]
    if (token == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
        return null
    }
    return try {
        val verifier =
            JWT
                .require(Algorithm.HMAC256(jwtConfig.secret))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
        val decoded = verifier.verify(token)
        val jti = decoded.id
        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Token revoked"))
            return null
        }
        UUID.fromString(decoded.subject)
    } catch (_: com.auth0.jwt.exceptions.JWTVerificationException) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
        null
    } catch (_: IllegalArgumentException) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
        null
    }
}

private suspend fun DefaultWebSocketServerSession.autoSubscribe(
    accountId: UUID,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
    householdService: HouseholdService,
    broadcastService: WebSocketBroadcastService,
) {
    val accessibleLists = shoppingListService.findAccessibleByAccount(accountId)
    accessibleLists.forEach { list ->
        sessionManager.subscribeToList(accountId, list.id.value)
        broadcastService.subscribeToListChannel(list.id.value)
    }

    householdService.findByAccountId(accountId).forEach { household ->
        sessionManager.subscribeToHousehold(accountId, household.id.value)
        broadcastService.subscribeToHouseholdChannel(household.id.value)
    }

    val listIds = accessibleLists.map { it.id.value.toString() }
    send(Frame.Text(json.encodeToString(SubscribedEvent.serializer(), SubscribedEvent(listIds = listIds))))
}

private suspend fun DefaultWebSocketServerSession.handleCommand(
    text: String,
    accountId: UUID,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
    broadcastService: WebSocketBroadcastService,
) {
    try {
        val type = json.decodeFromString<Map<String, String>>(text)["type"] ?: return
        when (type) {
            "subscribe" -> handleSubscribe(text, accountId, sessionManager, shoppingListService, broadcastService)
            "unsubscribe" -> handleUnsubscribe(text, accountId, sessionManager)
            "ping" -> send(Frame.Text(json.encodeToString(PongEvent.serializer(), PongEvent())))
            else -> sendError("Unknown command type: $type", "UNKNOWN_COMMAND")
        }
    } catch (_: kotlinx.serialization.SerializationException) {
        sendError("Failed to parse command", "PARSE_ERROR")
    }
}

private suspend fun DefaultWebSocketServerSession.handleSubscribe(
    text: String,
    accountId: UUID,
    sessionManager: WebSocketSessionManager,
    shoppingListService: ShoppingListService,
    broadcastService: WebSocketBroadcastService,
) {
    val command = json.decodeFromString<SubscribeCommand>(text)
    val validListIds =
        command.listIds.mapNotNull { listIdStr ->
            val listId = runCatching { UUID.fromString(listIdStr) }.getOrNull() ?: return@mapNotNull null
            if (shoppingListService.getPermission(listId, accountId, null) != null) {
                sessionManager.subscribeToList(accountId, listId)
                broadcastService.subscribeToListChannel(listId)
                listIdStr
            } else {
                null
            }
        }
    send(Frame.Text(json.encodeToString(SubscribedEvent.serializer(), SubscribedEvent(listIds = validListIds))))
}

private suspend fun DefaultWebSocketServerSession.handleUnsubscribe(
    text: String,
    accountId: UUID,
    sessionManager: WebSocketSessionManager,
) {
    val command = json.decodeFromString<UnsubscribeCommand>(text)
    command.listIds.forEach { listIdStr ->
        val listId = runCatching { UUID.fromString(listIdStr) }.getOrNull() ?: return@forEach
        sessionManager.unsubscribeFromList(accountId, listId)
        // Note: we don't unsubscribe from Valkey channel here since other local clients might still need it
    }
    send(Frame.Text(json.encodeToString(UnsubscribedEvent.serializer(), UnsubscribedEvent(listIds = command.listIds))))
}

private suspend fun DefaultWebSocketServerSession.sendError(
    message: String,
    code: String,
) {
    send(Frame.Text(json.encodeToString(ErrorEvent.serializer(), ErrorEvent(message = message, code = code))))
}
