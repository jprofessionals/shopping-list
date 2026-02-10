package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

// WebSocket events received from server

@Serializable
data class WsActorInfo(
    val id: String,
    val displayName: String,
)

@Serializable
data class WsItemData(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val checkedByName: String?,
)

@Serializable
data class WsListData(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
)

@Serializable
data class WsCommentData(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val editedAt: String?,
    val createdAt: String,
)

// Server -> Client events (use type field to discriminate)

@Serializable
data class WsSubscribedEvent(
    val type: String = "subscribed",
    val listIds: List<String>,
)

@Serializable
data class WsPongEvent(
    val type: String = "pong",
)

@Serializable
data class WsErrorEvent(
    val type: String = "error",
    val message: String,
    val code: String,
)

// Client -> Server commands

@Serializable
data class WsSubscribeCommand(
    val type: String = "subscribe",
    val listIds: List<String>,
)

@Serializable
data class WsUnsubscribeCommand(
    val type: String = "unsubscribe",
    val listIds: List<String>,
)

@Serializable
data class WsPingCommand(
    val type: String = "ping",
)
