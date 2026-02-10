package no.shoppinglist.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Comment
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class EventBroadcaster(
    private val broadcastService: WebSocketBroadcastService,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun broadcastItemAdded(
        item: ListItem,
        actorId: UUID,
    ) {
        scope.launch {
            val (listId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val listId = item.list.id.value
                    val event =
                        ItemAddedEvent(
                            listId = listId.toString(),
                            item = item.toItemData(),
                            actor = actor.toActorInfo(),
                        )
                    listId to event
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastItemUpdated(
        item: ListItem,
        changes: List<String>,
        actorId: UUID,
    ) {
        scope.launch {
            val (listId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val listId = item.list.id.value
                    val event =
                        ItemUpdatedEvent(
                            listId = listId.toString(),
                            item = item.toItemData(),
                            changes = changes,
                            actor = actor.toActorInfo(),
                        )
                    listId to event
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastItemChecked(
        item: ListItem,
        actorId: UUID,
    ) {
        scope.launch {
            val (listId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val listId = item.list.id.value
                    val event =
                        ItemCheckedEvent(
                            listId = listId.toString(),
                            itemId = item.id.value.toString(),
                            isChecked = item.isChecked,
                            actor = actor.toActorInfo(),
                        )
                    listId to event
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastItemRemoved(
        listId: UUID,
        itemId: UUID,
        actorId: UUID,
    ) {
        scope.launch {
            val event =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    ItemRemovedEvent(
                        listId = listId.toString(),
                        itemId = itemId.toString(),
                        actor = actor.toActorInfo(),
                    )
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastListCreated(
        list: ShoppingList,
        actorId: UUID,
    ) {
        scope.launch {
            val event =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    ListCreatedEvent(
                        list = list.toListData(),
                        actor = actor.toActorInfo(),
                    )
                } ?: return@launch

            // Broadcast to all household members if household list
            @Suppress("UNUSED_VARIABLE")
            val householdId = transaction { list.household?.id?.value }
        }
    }

    fun broadcastListUpdated(
        list: ShoppingList,
        changes: List<String>,
        actorId: UUID,
    ) {
        scope.launch {
            val (listId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val event =
                        ListUpdatedEvent(
                            list = list.toListData(),
                            changes = changes,
                            actor = actor.toActorInfo(),
                        )
                    list.id.value to event
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastListDeleted(
        listId: UUID,
        actorId: UUID,
    ) {
        scope.launch {
            val event =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    ListDeletedEvent(
                        listId = listId.toString(),
                        actor = actor.toActorInfo(),
                    )
                } ?: return@launch

            broadcastService.broadcastToList(listId, event)
        }
    }

    fun broadcastCommentAdded(
        comment: Comment,
        actorId: UUID,
    ) {
        scope.launch {
            val (targetType, targetId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val event =
                        CommentAddedEvent(
                            targetType = comment.targetType.name,
                            targetId = comment.targetId.toString(),
                            comment = comment.toCommentData(),
                            actor = actor.toActorInfo(),
                        )
                    Triple(comment.targetType, comment.targetId, event)
                } ?: return@launch

            broadcastCommentEvent(targetType, targetId, event)
        }
    }

    fun broadcastCommentUpdated(
        comment: Comment,
        actorId: UUID,
    ) {
        scope.launch {
            val (targetType, targetId, event) =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    val event =
                        CommentUpdatedEvent(
                            targetType = comment.targetType.name,
                            targetId = comment.targetId.toString(),
                            commentId = comment.id.value.toString(),
                            text = comment.text,
                            editedAt = comment.editedAt.toString(),
                            actor = actor.toActorInfo(),
                        )
                    Triple(comment.targetType, comment.targetId, event)
                } ?: return@launch

            broadcastCommentEvent(targetType, targetId, event)
        }
    }

    fun broadcastCommentDeleted(
        targetType: CommentTargetType,
        targetId: UUID,
        commentId: UUID,
        actorId: UUID,
    ) {
        scope.launch {
            val event =
                transaction {
                    val actor = Account.findById(actorId) ?: return@transaction null
                    CommentDeletedEvent(
                        targetType = targetType.name,
                        targetId = targetId.toString(),
                        commentId = commentId.toString(),
                        actor = actor.toActorInfo(),
                    )
                } ?: return@launch

            broadcastCommentEvent(targetType, targetId, event)
        }
    }

    private suspend fun broadcastCommentEvent(
        targetType: CommentTargetType,
        targetId: UUID,
        event: WebSocketEvent,
    ) {
        when (targetType) {
            CommentTargetType.LIST -> broadcastService.broadcastToList(targetId, event)
            CommentTargetType.HOUSEHOLD -> broadcastService.broadcastToHousehold(targetId, event)
        }
    }

    private fun Comment.toCommentData(): CommentData =
        CommentData(
            id = id.value.toString(),
            text = text,
            authorId = author.id.value.toString(),
            authorName = author.displayName,
            authorAvatarUrl = author.avatarUrl,
            editedAt = editedAt?.toString(),
            createdAt = createdAt.toString(),
        )

    private fun ListItem.toItemData(): ItemData =
        ItemData(
            id = id.value.toString(),
            name = name,
            quantity = quantity,
            unit = unit,
            isChecked = isChecked,
            checkedByName = checkedBy?.displayName,
        )

    private fun ShoppingList.toListData(): ListData =
        ListData(
            id = id.value.toString(),
            name = name,
            householdId = household?.id?.value?.toString(),
            isPersonal = isPersonal,
        )

    private fun Account.toActorInfo(): ActorInfo =
        ActorInfo(
            id = id.value.toString(),
            displayName = displayName,
        )
}
