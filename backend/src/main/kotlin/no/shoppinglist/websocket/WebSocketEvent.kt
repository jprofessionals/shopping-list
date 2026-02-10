package no.shoppinglist.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
sealed class WebSocketEvent {
    abstract val type: String
    abstract val timestamp: String
}

@Serializable
data class ActorInfo(
    val id: String,
    val displayName: String,
)

// Item events
@Serializable
@SerialName("item:added")
data class ItemAddedEvent(
    override val type: String = "item:added",
    val listId: String,
    val item: ItemData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:updated")
data class ItemUpdatedEvent(
    override val type: String = "item:updated",
    val listId: String,
    val item: ItemData,
    val changes: List<String>,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:checked")
data class ItemCheckedEvent(
    override val type: String = "item:checked",
    val listId: String,
    val itemId: String,
    val isChecked: Boolean,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("item:removed")
data class ItemRemovedEvent(
    override val type: String = "item:removed",
    val listId: String,
    val itemId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// List events
@Serializable
@SerialName("list:created")
data class ListCreatedEvent(
    override val type: String = "list:created",
    val list: ListData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("list:updated")
data class ListUpdatedEvent(
    override val type: String = "list:updated",
    val list: ListData,
    val changes: List<String>,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("list:deleted")
data class ListDeletedEvent(
    override val type: String = "list:deleted",
    val listId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// Comment events
@Serializable
@SerialName("comment:added")
data class CommentAddedEvent(
    override val type: String = "comment:added",
    val targetType: String,
    val targetId: String,
    val comment: CommentData,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("comment:updated")
data class CommentUpdatedEvent(
    override val type: String = "comment:updated",
    val targetType: String,
    val targetId: String,
    val commentId: String,
    val text: String,
    val editedAt: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

@Serializable
@SerialName("comment:deleted")
data class CommentDeletedEvent(
    override val type: String = "comment:deleted",
    val targetType: String,
    val targetId: String,
    val commentId: String,
    val actor: ActorInfo,
    override val timestamp: String = Instant.now().toString(),
) : WebSocketEvent()

// Data transfer objects for events
@Serializable
data class ItemData(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val checkedByName: String?,
)

@Serializable
data class ListData(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
)

@Serializable
data class CommentData(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val editedAt: String?,
    val createdAt: String,
)
