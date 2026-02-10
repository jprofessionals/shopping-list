package no.shoppinglist.shared.websocket

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.shared.api.TokenStore
import no.shoppinglist.shared.api.dto.WsActorInfo
import no.shoppinglist.shared.api.dto.WsCommentData
import no.shoppinglist.shared.api.dto.WsItemData
import no.shoppinglist.shared.api.dto.WsListData
import no.shoppinglist.shared.api.dto.WsPingCommand
import no.shoppinglist.shared.api.dto.WsSubscribeCommand
import no.shoppinglist.shared.api.dto.WsUnsubscribeCommand

sealed class WebSocketEvent {
    data class ItemAdded(
        val listId: String,
        val item: WsItemData,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class ItemUpdated(
        val listId: String,
        val item: WsItemData,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class ItemChecked(
        val listId: String,
        val itemId: String,
        val isChecked: Boolean,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class ItemRemoved(
        val listId: String,
        val itemId: String,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class ListUpdated(
        val list: WsListData,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class ListDeleted(
        val listId: String,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class CommentAdded(
        val listId: String,
        val comment: WsCommentData,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class CommentUpdated(
        val listId: String,
        val comment: WsCommentData,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class CommentDeleted(
        val listId: String,
        val commentId: String,
        val actor: WsActorInfo,
    ) : WebSocketEvent()

    data class Subscribed(val listIds: List<String>) : WebSocketEvent()

    data object Pong : WebSocketEvent()

    data class Error(val message: String, val code: String) : WebSocketEvent()

    data class ConnectionStateChanged(val connected: Boolean) : WebSocketEvent()
}

class WebSocketClient(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private var shouldReconnect = false
    private var reconnectAttempt = 0

    companion object {
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    fun connect() {
        shouldReconnect = true
        reconnectAttempt = 0
        connectionJob?.cancel()
        connectionJob = scope.launch {
            connectWithRetry()
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectAttempt = 0
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            try {
                session?.close()
            } catch (_: Exception) {
                // Ignore close errors
            }
            session = null
            _isConnected.value = false
            _events.emit(WebSocketEvent.ConnectionStateChanged(connected = false))
        }
    }

    fun subscribe(listIds: List<String>) {
        scope.launch {
            sendCommand(json.encodeToString(WsSubscribeCommand.serializer(), WsSubscribeCommand(listIds = listIds)))
        }
    }

    fun unsubscribe(listIds: List<String>) {
        scope.launch {
            sendCommand(json.encodeToString(WsUnsubscribeCommand.serializer(), WsUnsubscribeCommand(listIds = listIds)))
        }
    }

    fun ping() {
        scope.launch {
            sendCommand(json.encodeToString(WsPingCommand.serializer(), WsPingCommand()))
        }
    }

    private suspend fun sendCommand(text: String) {
        try {
            session?.send(Frame.Text(text))
        } catch (e: Exception) {
            // Connection may have dropped; reconnect will handle it
        }
    }

    private suspend fun connectWithRetry() {
        while (shouldReconnect) {
            try {
                val token = tokenStore.getAccessToken() ?: run {
                    delay(INITIAL_BACKOFF_MS)
                    return@connectWithRetry
                }

                val wsUrl = baseUrl
                    .replace("http://", "ws://")
                    .replace("https://", "wss://")

                val wsSession = httpClient.webSocketSession("$wsUrl/ws?token=$token")
                session = wsSession
                _isConnected.value = true
                reconnectAttempt = 0
                _events.emit(WebSocketEvent.ConnectionStateChanged(connected = true))

                // Read incoming frames
                for (frame in wsSession.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        parseAndEmitEvent(text)
                    }
                }

                // Connection closed normally
                _isConnected.value = false
                session = null
                _events.emit(WebSocketEvent.ConnectionStateChanged(connected = false))
            } catch (e: Exception) {
                _isConnected.value = false
                session = null
                _events.emit(WebSocketEvent.ConnectionStateChanged(connected = false))
            }

            if (shouldReconnect) {
                val backoff = calculateBackoff()
                reconnectAttempt++
                delay(backoff)
            }
        }
    }

    private fun calculateBackoff(): Long {
        val backoff = INITIAL_BACKOFF_MS * (1L shl minOf(reconnectAttempt, 4))
        return minOf(backoff, MAX_BACKOFF_MS)
    }

    private suspend fun parseAndEmitEvent(text: String) {
        try {
            val jsonObject = json.decodeFromString(JsonObject.serializer(), text)
            val type = jsonObject["type"]?.jsonPrimitive?.content ?: return

            val event = when (type) {
                "subscribed" -> {
                    val listIds = jsonObject["listIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    WebSocketEvent.Subscribed(listIds)
                }
                "pong" -> WebSocketEvent.Pong
                "error" -> {
                    val message = jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    val code = jsonObject["code"]?.jsonPrimitive?.content ?: "UNKNOWN"
                    WebSocketEvent.Error(message, code)
                }
                "item:added" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content ?: return
                    val item = json.decodeFromString(WsItemData.serializer(), jsonObject["item"].toString())
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ItemAdded(listId, item, actor)
                }
                "item:updated" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content ?: return
                    val item = json.decodeFromString(WsItemData.serializer(), jsonObject["item"].toString())
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ItemUpdated(listId, item, actor)
                }
                "item:checked" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content ?: return
                    val itemId = jsonObject["itemId"]?.jsonPrimitive?.content ?: return
                    val isChecked = jsonObject["isChecked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ItemChecked(listId, itemId, isChecked, actor)
                }
                "item:removed" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content ?: return
                    val itemId = jsonObject["itemId"]?.jsonPrimitive?.content ?: return
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ItemRemoved(listId, itemId, actor)
                }
                "list:updated" -> {
                    val list = json.decodeFromString(WsListData.serializer(), jsonObject["list"].toString())
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ListUpdated(list, actor)
                }
                "list:deleted" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content ?: return
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.ListDeleted(listId, actor)
                }
                "comment:added" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content
                        ?: jsonObject["targetId"]?.jsonPrimitive?.content ?: return
                    val comment = json.decodeFromString(WsCommentData.serializer(), jsonObject["comment"].toString())
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.CommentAdded(listId, comment, actor)
                }
                "comment:updated" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content
                        ?: jsonObject["targetId"]?.jsonPrimitive?.content ?: return
                    val comment = json.decodeFromString(WsCommentData.serializer(), jsonObject["comment"].toString())
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.CommentUpdated(listId, comment, actor)
                }
                "comment:deleted" -> {
                    val listId = jsonObject["listId"]?.jsonPrimitive?.content
                        ?: jsonObject["targetId"]?.jsonPrimitive?.content ?: return
                    val commentId = jsonObject["commentId"]?.jsonPrimitive?.content ?: return
                    val actor = json.decodeFromString(WsActorInfo.serializer(), jsonObject["actor"].toString())
                    WebSocketEvent.CommentDeleted(listId, commentId, actor)
                }
                else -> null
            }

            if (event != null) {
                _events.emit(event)
            }
        } catch (_: Exception) {
            // Silently ignore malformed messages
        }
    }
}
