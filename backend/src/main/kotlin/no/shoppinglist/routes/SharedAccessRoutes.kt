package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import org.jetbrains.exposed.sql.transactions.transaction

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

fun Route.sharedAccessRoutes(
    listShareService: ListShareService,
    listItemService: ListItemService,
) {
    route("/shared/{token}") {
        getSharedListRoute(listShareService, listItemService)
    }
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
