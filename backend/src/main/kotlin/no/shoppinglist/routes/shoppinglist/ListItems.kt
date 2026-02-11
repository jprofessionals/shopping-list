package no.shoppinglist.routes.shoppinglist

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.domain.ActivityType
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.ItemHistoryService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.RecurringItemService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.websocket.EventBroadcaster
import org.jetbrains.exposed.sql.transactions.transaction

@Suppress("LongParameterList")
internal fun Route.listItemRoutes(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
    itemHistoryService: ItemHistoryService,
    recurringItemService: RecurringItemService,
) {
    route("/items") {
        addItemRoute(shoppingListService, listItemService, eventBroadcaster, activityService, itemHistoryService)
        clearCheckedRoute(shoppingListService, listItemService)
        bulkCreateRoute(shoppingListService, listItemService, itemHistoryService)
        route("/{itemId}") {
            updateItemRoute(shoppingListService, listItemService, eventBroadcaster, activityService)
            deleteItemRoute(shoppingListService, listItemService, eventBroadcaster, activityService)
            checkItemRoute(
                shoppingListService,
                listItemService,
                eventBroadcaster,
                activityService,
                recurringItemService,
            )
        }
    }
}

private fun Route.addItemRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
    itemHistoryService: ItemHistoryService,
) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<CreateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@post call.respond(HttpStatusCode.BadRequest, itemNameError())
        }

        val item = listItemService.create(listId, request.name, request.quantity, request.unit, null, accountId)
        activityService.recordActivity(listId, accountId, ActivityType.ITEM_ADDED, item.name)
        itemHistoryService.recordItemUsage(accountId, item.name, item.quantity, item.unit)
        eventBroadcaster.broadcastItemAdded(item, accountId)
        call.respond(HttpStatusCode.Created, transaction { item.toResponse() })
    }
}

private fun Route.clearCheckedRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
) {
    delete("/checked") {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val deletedIds = listItemService.deleteCheckedItems(listId)
        call.respond(mapOf("deletedItemIds" to deletedIds.map { it.toString() }))
    }
}

private fun Route.bulkCreateRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    itemHistoryService: ItemHistoryService,
) {
    post("/bulk") {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<BulkCreateItemsRequest>()
        if (request.items.any { it.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH }) {
            return@post call.respond(HttpStatusCode.BadRequest, itemNameError())
        }

        val items =
            request.items.map { itemReq ->
                val item = listItemService.create(listId, itemReq.name, itemReq.quantity, itemReq.unit, null, accountId)
                itemHistoryService.recordItemUsage(accountId, item.name, item.quantity, item.unit)
                item
            }

        call.respond(HttpStatusCode.Created, items.map { transaction { it.toResponse() } })
    }
}

private fun Route.updateItemRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
) {
    patch {
        val accountId =
            call.getAccountId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)
        val itemId =
            call.uuidParam("itemId")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) != SharePermission.WRITE) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<UpdateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@patch call.respond(HttpStatusCode.BadRequest, itemNameError())
        }

        val updated =
            listItemService.update(itemId, request.name, request.quantity, request.unit)
                ?: return@patch call.respond(HttpStatusCode.NotFound)

        activityService.recordActivity(listId, accountId, ActivityType.ITEM_UPDATED, updated.name)
        eventBroadcaster.broadcastItemUpdated(updated, listOf("name", "quantity", "unit"), accountId)
        call.respond(transaction { updated.toResponse() })
    }
}

private fun Route.deleteItemRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
) {
    delete {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val itemId =
            call.uuidParam("itemId")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val itemName =
            listItemService.findById(itemId)?.name
                ?: return@delete call.respond(HttpStatusCode.NotFound)

        listItemService.delete(itemId)
        activityService.recordActivity(listId, accountId, ActivityType.ITEM_REMOVED, itemName)
        eventBroadcaster.broadcastItemRemoved(listId, itemId, accountId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.checkItemRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
    recurringItemService: RecurringItemService,
) {
    post("/check") {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)
        val itemId =
            call.uuidParam("itemId")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        val permission = shoppingListService.getPermission(listId, accountId, null)
        if (permission != SharePermission.CHECK && permission != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val toggled =
            listItemService.toggleCheck(itemId, accountId)
                ?: return@post call.respond(HttpStatusCode.NotFound)

        // Update lastPurchased on the recurring item when checked
        if (toggled.isChecked) {
            val recurringItemId = transaction { toggled.recurringItem?.id?.value }
            if (recurringItemId != null) {
                recurringItemService.markPurchased(recurringItemId)
            }
        }

        val activityType = if (toggled.isChecked) ActivityType.ITEM_CHECKED else ActivityType.ITEM_UNCHECKED
        activityService.recordActivity(listId, accountId, activityType, toggled.name)
        eventBroadcaster.broadcastItemChecked(toggled, accountId)
        call.respond(transaction { toggled.toResponse() })
    }
}

private fun itemNameError() =
    mapOf(
        "error" to "Item name must be between $ITEM_NAME_MIN_LENGTH and $ITEM_NAME_MAX_LENGTH characters",
    )
