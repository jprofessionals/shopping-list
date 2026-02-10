package no.shoppinglist.routes.shoppinglist

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.ShoppingListService
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

internal fun Route.listCrudRoutes(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    householdService: HouseholdService,
    pinnedListService: PinnedListService,
) {
    listAllRoute(shoppingListService, pinnedListService)
    createListRoute(shoppingListService, householdService)
    route("/{id}") {
        getListRoute(shoppingListService, listItemService, pinnedListService)
        updateListRoute(shoppingListService)
        deleteListRoute(shoppingListService)
    }
}

private fun Route.listAllRoute(
    shoppingListService: ShoppingListService,
    pinnedListService: PinnedListService,
) {
    get {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val pinnedIds = pinnedListService.getPinnedListIds(accountId)
        val summaries = shoppingListService.findAccessibleByAccountWithSummary(accountId, pinnedIds)
        call.respond(
            summaries.map { s ->
                transaction {
                    s.list.toListResponse(accountId).copy(
                        itemCount = s.itemCount,
                        uncheckedCount = s.uncheckedCount,
                        previewItems = s.previewItems,
                        isPinned = s.isPinned,
                    )
                }
            },
        )
    }
}

private fun Route.createListRoute(
    shoppingListService: ShoppingListService,
    householdService: HouseholdService,
) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val request = call.receive<CreateListRequest>()
        if (request.name.length !in NAME_MIN_LENGTH..NAME_MAX_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Name must be between $NAME_MIN_LENGTH and $NAME_MAX_LENGTH characters"),
            )
        }

        val householdId = request.householdId?.let { UUID.fromString(it) }
        if (householdId != null && !householdService.isMember(householdId, accountId)) {
            return@post call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "You are not a member of this household"),
            )
        }

        val list = shoppingListService.create(request.name, accountId, householdId, request.isPersonal)
        call.respond(HttpStatusCode.Created, transaction { list.toListResponse(accountId) })
    }
}

private fun Route.getListRoute(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    pinnedListService: PinnedListService,
) {
    get {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@get call.respond(HttpStatusCode.BadRequest)

        val list =
            shoppingListService.findById(listId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
        if (shoppingListService.getPermission(listId, accountId, null) == null) {
            return@get call.respond(HttpStatusCode.Forbidden)
        }

        val items = listItemService.findByListId(listId)
        val isPinned = pinnedListService.isPinned(accountId, listId)
        call.respond(buildDetailResponse(list, accountId, items, isPinned))
    }
}

private fun buildDetailResponse(
    list: no.shoppinglist.domain.ShoppingList,
    accountId: UUID,
    items: List<no.shoppinglist.domain.ListItem>,
    isPinned: Boolean,
) = transaction {
    ListDetailResponse(
        id = list.id.value.toString(),
        name = list.name,
        householdId =
            list.household
                ?.id
                ?.value
                ?.toString(),
        isPersonal = list.isPersonal,
        createdAt = list.createdAt.toString(),
        isOwner = list.owner.id.value == accountId,
        items = items.map { it.toResponse() },
        isPinned = isPinned,
    )
}

private fun Route.updateListRoute(shoppingListService: ShoppingListService) {
    patch {
        val accountId =
            call.getAccountId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) == null) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<UpdateListRequest>()
        if (request.name.length !in NAME_MIN_LENGTH..NAME_MAX_LENGTH) {
            return@patch call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Name must be between $NAME_MIN_LENGTH and $NAME_MAX_LENGTH characters"),
            )
        }

        val updated =
            shoppingListService.update(listId, request.name, request.isPersonal)
                ?: return@patch call.respond(HttpStatusCode.NotFound)
        call.respond(transaction { updated.toListResponse(accountId) })
    }
}

private fun Route.deleteListRoute(shoppingListService: ShoppingListService) {
    delete {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val list =
            shoppingListService.findById(listId)
                ?: return@delete call.respond(HttpStatusCode.NotFound)

        val isOwner = transaction { list.owner.id.value == accountId }
        if (!isOwner) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        shoppingListService.delete(listId)
        call.respond(HttpStatusCode.NoContent)
    }
}
