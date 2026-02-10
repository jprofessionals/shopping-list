package no.shoppinglist.routes.shoppinglist

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.ShoppingListService

internal fun Route.listPinningRoutes(
    shoppingListService: ShoppingListService,
    pinnedListService: PinnedListService,
) {
    post("/pin") {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        val permission = shoppingListService.getPermission(listId, accountId, null)
        if (permission == null) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        pinnedListService.pin(accountId, listId)
        call.respond(HttpStatusCode.OK, mapOf("pinned" to true))
    }

    delete("/pin") {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)

        val listId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        pinnedListService.unpin(accountId, listId)
        call.respond(HttpStatusCode.OK, mapOf("pinned" to false))
    }
}
