package no.shoppinglist.websocket

import kotlinx.serialization.json.Json
import no.shoppinglist.service.ValkeyService
import org.slf4j.LoggerFactory
import java.util.UUID

class WebSocketBroadcastService(
    private val valkeyService: ValkeyService,
    private val sessionManager: WebSocketSessionManager,
) {
    private val logger = LoggerFactory.getLogger(WebSocketBroadcastService::class.java)

    private val json =
        Json {
            encodeDefaults = true
            classDiscriminator = "_class"
        }

    private val subscribedChannels = mutableSetOf<String>()

    suspend fun broadcastToList(
        listId: UUID,
        event: WebSocketEvent,
        excludeAccountId: UUID? = null,
    ) {
        val channel = "ws:list:$listId"
        val message = json.encodeToString(WebSocketEvent.serializer(), event)
        val envelope =
            json.encodeToString(
                BroadcastEnvelope.serializer(),
                BroadcastEnvelope(
                    channel = channel,
                    targetId = listId.toString(),
                    excludeAccountId = excludeAccountId?.toString(),
                    payload = message,
                ),
            )

        val published = valkeyService.publish(channel, envelope)
        if (!published) {
            // Fallback to local broadcast if Valkey is down
            sessionManager.broadcastToList(listId, event, excludeAccountId)
        }
    }

    suspend fun broadcastToHousehold(
        householdId: UUID,
        event: WebSocketEvent,
        excludeAccountId: UUID? = null,
    ) {
        val channel = "ws:household:$householdId"
        val message = json.encodeToString(WebSocketEvent.serializer(), event)
        val envelope =
            json.encodeToString(
                BroadcastEnvelope.serializer(),
                BroadcastEnvelope(
                    channel = channel,
                    targetId = householdId.toString(),
                    excludeAccountId = excludeAccountId?.toString(),
                    payload = message,
                ),
            )

        val published = valkeyService.publish(channel, envelope)
        if (!published) {
            sessionManager.broadcastToHousehold(householdId, event, excludeAccountId)
        }
    }

    fun subscribeToListChannel(listId: UUID) {
        val channel = "ws:list:$listId"
        synchronized(subscribedChannels) {
            if (channel in subscribedChannels) return
            subscribedChannels.add(channel)
        }
        valkeyService.subscribe(channel) { _, message ->
            handleBroadcast(message)
        }
    }

    fun unsubscribeFromListChannel(listId: UUID) {
        val channel = "ws:list:$listId"
        synchronized(subscribedChannels) {
            subscribedChannels.remove(channel)
        }
        valkeyService.unsubscribe(channel)
    }

    fun subscribeToHouseholdChannel(householdId: UUID) {
        val channel = "ws:household:$householdId"
        synchronized(subscribedChannels) {
            if (channel in subscribedChannels) return
            subscribedChannels.add(channel)
        }
        valkeyService.subscribe(channel) { _, message ->
            handleBroadcast(message)
        }
    }

    fun unsubscribeFromHouseholdChannel(householdId: UUID) {
        val channel = "ws:household:$householdId"
        synchronized(subscribedChannels) {
            subscribedChannels.remove(channel)
        }
        valkeyService.unsubscribe(channel)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleBroadcast(message: String) {
        try {
            val envelope = json.decodeFromString(BroadcastEnvelope.serializer(), message)
            val event = json.decodeFromString(WebSocketEvent.serializer(), envelope.payload)
            val excludeId = envelope.excludeAccountId?.let { UUID.fromString(it) }
            val targetId = UUID.fromString(envelope.targetId)

            if (envelope.channel.startsWith("ws:list:")) {
                sessionManager.broadcastToList(targetId, event, excludeId)
            } else if (envelope.channel.startsWith("ws:household:")) {
                sessionManager.broadcastToHousehold(targetId, event, excludeId)
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle broadcast message: ${e.message}")
        }
    }
}

@kotlinx.serialization.Serializable
data class BroadcastEnvelope(
    val channel: String,
    val targetId: String,
    val excludeAccountId: String?,
    val payload: String,
)
