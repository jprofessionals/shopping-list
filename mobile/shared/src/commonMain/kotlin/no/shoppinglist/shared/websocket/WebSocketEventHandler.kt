package no.shoppinglist.shared.websocket

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import no.shoppinglist.shared.api.dto.WsItemData
import no.shoppinglist.shared.cache.ShoppingListDatabase
import no.shoppinglist.shared.repository.AuthRepository

sealed class NotificationEvent {
    data class NewList(val listName: String, val actorName: String) : NotificationEvent()
    data class ItemAdded(val listName: String, val itemName: String, val actorName: String) : NotificationEvent()
    data class NewComment(val targetName: String, val authorName: String, val text: String) : NotificationEvent()
}

class WebSocketEventHandler(
    private val webSocketClient: WebSocketClient,
    private val authRepository: AuthRepository,
    private val database: ShoppingListDatabase,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val itemQueries = database.listItemQueries
    private val listQueries = database.shoppingListQueries
    private var subscribedListIds = emptySet<String>()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 16)
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

    fun start() {
        observeAuthState()
        observeListsForSubscription()
        processEvents()
    }

    fun stop() {
        webSocketClient.disconnect()
        scope.cancel()
    }

    private fun observeAuthState() {
        authRepository.isLoggedIn
            .onEach { loggedIn ->
                if (loggedIn) {
                    webSocketClient.connect()
                } else {
                    webSocketClient.disconnect()
                    subscribedListIds = emptySet()
                }
            }
            .launchIn(scope)
    }

    private fun observeListsForSubscription() {
        listQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
            .combine(webSocketClient.isConnected) { lists, connected -> lists to connected }
            .onEach { (lists, connected) ->
                if (!connected) {
                    subscribedListIds = emptySet()
                    return@onEach
                }
                val currentIds = lists.map { it.id }.toSet()
                if (currentIds != subscribedListIds) {
                    if (currentIds.isNotEmpty()) {
                        webSocketClient.subscribe(currentIds.toList())
                    }
                    subscribedListIds = currentIds
                }
            }
            .launchIn(scope)
    }

    private fun processEvents() {
        webSocketClient.events
            .onEach { event -> handleEvent(event) }
            .launchIn(scope)
    }

    private fun handleEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.ItemAdded -> {
                itemQueries.insert(
                    id = event.item.id,
                    listId = event.listId,
                    name = event.item.name,
                    quantity = event.item.quantity,
                    unit = event.item.unit,
                    isChecked = event.item.isChecked,
                    checkedByName = event.item.checkedByName,
                    createdAt = Clock.System.now().toString(),
                )
                recalculateListCounts(event.listId)
                val listName = listQueries.selectById(event.listId).executeAsOneOrNull()?.name ?: event.listId
                emitNotification(
                    NotificationEvent.ItemAdded(listName, event.item.name, event.actor.displayName),
                    event.actor.id,
                )
            }
            is WebSocketEvent.ItemUpdated -> {
                upsertItem(event.listId, event.item)
            }
            is WebSocketEvent.ItemChecked -> {
                val existing = itemQueries.selectById(event.itemId).executeAsOneOrNull()
                if (existing != null) {
                    itemQueries.insert(
                        id = existing.id,
                        listId = existing.listId,
                        name = existing.name,
                        quantity = existing.quantity,
                        unit = existing.unit,
                        isChecked = event.isChecked,
                        checkedByName = if (event.isChecked) event.actor.displayName else null,
                        createdAt = existing.createdAt,
                    )
                    recalculateListCounts(existing.listId)
                }
            }
            is WebSocketEvent.ItemRemoved -> {
                itemQueries.deleteById(event.itemId)
                recalculateListCounts(event.listId)
            }
            is WebSocketEvent.ListCreated -> {
                listQueries.insert(
                    id = event.list.id,
                    name = event.list.name,
                    householdId = event.list.householdId,
                    isPersonal = event.list.isPersonal,
                    createdAt = Clock.System.now().toString(),
                    isOwner = false,
                    itemCount = 0,
                    uncheckedCount = 0,
                    isPinned = false,
                )
                emitNotification(
                    NotificationEvent.NewList(event.list.name, event.actor.displayName),
                    event.actor.id,
                )
            }
            is WebSocketEvent.ListUpdated -> {
                val existing = listQueries.selectById(event.list.id).executeAsOneOrNull()
                if (existing != null) {
                    listQueries.insert(
                        id = existing.id,
                        name = event.list.name,
                        householdId = event.list.householdId,
                        isPersonal = event.list.isPersonal,
                        createdAt = existing.createdAt,
                        isOwner = existing.isOwner,
                        itemCount = existing.itemCount,
                        uncheckedCount = existing.uncheckedCount,
                        isPinned = existing.isPinned,
                    )
                }
            }
            is WebSocketEvent.ListDeleted -> {
                listQueries.deleteById(event.listId)
                itemQueries.deleteByListId(event.listId)
            }
            is WebSocketEvent.CommentAdded -> {
                emitNotification(
                    NotificationEvent.NewComment(event.listId, event.actor.displayName, event.comment.text),
                    event.actor.id,
                )
            }
            is WebSocketEvent.CommentUpdated,
            is WebSocketEvent.CommentDeleted,
            is WebSocketEvent.Subscribed,
            is WebSocketEvent.Pong,
            is WebSocketEvent.Error,
            is WebSocketEvent.ConnectionStateChanged -> { /* No DB action needed */ }
        }
    }

    private fun upsertItem(listId: String, item: WsItemData) {
        val existing = itemQueries.selectById(item.id).executeAsOneOrNull()
        itemQueries.insert(
            id = item.id,
            listId = listId,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            isChecked = item.isChecked,
            checkedByName = item.checkedByName,
            createdAt = existing?.createdAt ?: Clock.System.now().toString(),
        )
        recalculateListCounts(listId)
    }

    private fun recalculateListCounts(listId: String) {
        val items = itemQueries.selectByListId(listId).executeAsList()
        val total = items.size.toLong()
        val unchecked = items.count { !it.isChecked }.toLong()
        val existing = listQueries.selectById(listId).executeAsOneOrNull() ?: return
        listQueries.insert(
            id = existing.id,
            name = existing.name,
            householdId = existing.householdId,
            isPersonal = existing.isPersonal,
            createdAt = existing.createdAt,
            isOwner = existing.isOwner,
            itemCount = total,
            uncheckedCount = unchecked,
            isPinned = existing.isPinned,
        )
    }

    private fun emitNotification(event: NotificationEvent, actorId: String) {
        val currentUserId = authRepository.currentUser.value?.id
        if (currentUserId != null && actorId != currentUserId) {
            _notificationEvents.tryEmit(event)
        }
    }
}
