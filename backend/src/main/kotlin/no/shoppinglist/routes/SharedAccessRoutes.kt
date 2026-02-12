package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.routes.shoppinglist.CreateItemRequest
import no.shoppinglist.routes.shoppinglist.ITEM_NAME_MAX_LENGTH
import no.shoppinglist.routes.shoppinglist.ITEM_NAME_MIN_LENGTH
import no.shoppinglist.routes.shoppinglist.UpdateItemRequest
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class SharedListResponse(
    val id: String,
    val name: String,
    val permission: String,
    val items: List<SharedItemResponse>,
)

@Serializable
data class SharedItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
)

data class ShareContext(
    val listId: UUID,
    val permission: SharePermission,
)

fun Route.sharedAccessRoutes(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    route("/shared/{token}") {
        getSharedListRoute(listShareService, listItemService)
        route("/items") {
            addItemRoute(listShareService, listItemService)
            clearCheckedRoute(listShareService, listItemService)
            route("/{itemId}") {
                checkItemRoute(listShareService, listItemService)
                updateItemRoute(listShareService, listItemService)
                deleteItemRoute(listShareService, listItemService)
            }
        }
    }
}

private suspend fun RoutingContext.validateShareToken(
    listShareService: ListShareService,
): ShareContext? {
    val token = call.parameters["token"]
    val share = token?.let { listShareService.findByToken(it) }

    if (share == null) {
        if (token != null) respondToMissingShare(listShareService, token)
        return null
    }
    return transaction {
        ShareContext(listId = share.list.id.value, permission = share.permission)
    }
}

private suspend fun RoutingContext.respondToMissingShare(
    listShareService: ListShareService,
    token: String,
): Nothing? {
    if (listShareService.isTokenExpired(token)) {
        call.respond(HttpStatusCode.Gone, mapOf("error" to "This link has expired"))
    } else {
        call.respond(HttpStatusCode.NotFound)
    }
    return null
}

private fun Route.getSharedListRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    get {
        val token =
            call.parameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

        val share = listShareService.findByToken(token)

        if (share == null) {
            if (listShareService.isTokenExpired(token)) {
                return@get call.respond(HttpStatusCode.Gone, mapOf("error" to "This link has expired"))
            }
            return@get call.respond(HttpStatusCode.NotFound)
        }

        val list = transaction { share.list }
        val items = listItemService.findByListId(list.id.value)
        call.respond(HttpStatusCode.OK, buildSharedListResponse(list, share, items))
    }
}

private fun parseItemId(raw: String?): UUID? =
    raw?.let {
        try {
            UUID.fromString(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

private suspend fun RoutingContext.resolveSharedItem(
    ctx: ShareContext,
    listItemService: ListItemService,
): UUID? {
    val itemId = parseItemId(call.parameters["itemId"])
        ?: return null.also { call.respond(HttpStatusCode.BadRequest) }

    val item = listItemService.findById(itemId)
    val belongsToList = item != null && transaction { item.list.id.value } == ctx.listId
    if (!belongsToList) call.respond(HttpStatusCode.NotFound)
    return itemId.takeIf { belongsToList }
}

private fun Route.checkItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    post("/check") {
        val ctx = validateShareToken(listShareService) ?: return@post

        if (ctx.permission != SharePermission.CHECK &&
            ctx.permission != SharePermission.WRITE
        ) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = resolveSharedItem(ctx, listItemService) ?: return@post
        val updated = listItemService.toggleCheckAnonymous(itemId)
            ?: return@post call.respond(HttpStatusCode.NotFound)

        call.respond(HttpStatusCode.OK, buildSharedItemResponse(updated))
    }
}

private fun Route.addItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    post {
        val ctx = validateShareToken(listShareService) ?: return@post
        if (ctx.permission != SharePermission.WRITE) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<CreateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to
                        "Item name must be between $ITEM_NAME_MIN_LENGTH and $ITEM_NAME_MAX_LENGTH characters",
                ),
            )
        }

        val item = listItemService.createForSharedAccess(
            ctx.listId,
            request.name,
            request.quantity,
            request.unit,
        )
        call.respond(HttpStatusCode.Created, buildSharedItemResponse(item))
    }
}

private fun Route.updateItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    patch {
        val ctx = validateShareToken(listShareService) ?: return@patch
        if (ctx.permission != SharePermission.WRITE) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = resolveSharedItem(ctx, listItemService) ?: return@patch

        val request = call.receive<UpdateItemRequest>()
        if (request.name.length !in ITEM_NAME_MIN_LENGTH..ITEM_NAME_MAX_LENGTH) {
            return@patch call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to
                        "Item name must be between $ITEM_NAME_MIN_LENGTH and $ITEM_NAME_MAX_LENGTH characters",
                ),
            )
        }

        val updated = listItemService.update(itemId, request.name, request.quantity, request.unit)
            ?: return@patch call.respond(HttpStatusCode.NotFound)

        call.respond(HttpStatusCode.OK, buildSharedItemResponse(updated))
    }
}

private fun Route.deleteItemRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    delete {
        val ctx = validateShareToken(listShareService) ?: return@delete
        if (ctx.permission != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val itemId = resolveSharedItem(ctx, listItemService) ?: return@delete

        listItemService.delete(itemId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.clearCheckedRoute(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    delete("/checked") {
        val ctx = validateShareToken(listShareService) ?: return@delete
        if (ctx.permission != SharePermission.WRITE) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val deletedIds = listItemService.deleteCheckedItems(ctx.listId)
        call.respond(mapOf("deletedItemIds" to deletedIds.map { it.toString() }))
    }
}

private fun buildSharedItemResponse(item: ListItem) =
    transaction {
        SharedItemResponse(
            id = item.id.value.toString(),
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            isChecked = item.isChecked,
        )
    }

private fun buildSharedListResponse(
    list: ShoppingList,
    share: ListShare,
    items: List<ListItem>,
) = transaction {
    SharedListResponse(
        id = list.id.value.toString(),
        name = list.name,
        permission = share.permission.name,
        items =
            items.map { item ->
                SharedItemResponse(
                    id = item.id.value.toString(),
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    isChecked = item.isChecked,
                )
            },
    )
}
