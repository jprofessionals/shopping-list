package no.shoppinglist.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class WebSocketCommand {
    abstract val type: String
}

@Serializable
@SerialName("subscribe")
data class SubscribeCommand(
    override val type: String = "subscribe",
    val listIds: List<String>,
) : WebSocketCommand()

@Serializable
@SerialName("unsubscribe")
data class UnsubscribeCommand(
    override val type: String = "unsubscribe",
    val listIds: List<String>,
) : WebSocketCommand()

@Serializable
@SerialName("ping")
data class PingCommand(
    override val type: String = "ping",
) : WebSocketCommand()

// Response events sent to client
@Serializable
data class SubscribedEvent(
    val type: String = "subscribed",
    val listIds: List<String>,
)

@Serializable
data class UnsubscribedEvent(
    val type: String = "unsubscribed",
    val listIds: List<String>,
)

@Serializable
data class PongEvent(
    val type: String = "pong",
)

@Serializable
data class ErrorEvent(
    val type: String = "error",
    val message: String,
    val code: String,
)
