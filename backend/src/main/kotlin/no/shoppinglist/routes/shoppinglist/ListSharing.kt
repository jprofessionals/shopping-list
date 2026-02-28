package no.shoppinglist.routes.shoppinglist

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShareType
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.ShoppingListService
import org.jetbrains.exposed.sql.transactions.transaction

internal fun Route.listSharingRoutes(
    shoppingListService: ShoppingListService,
    listShareService: ListShareService,
) {
    route("/shares") {
        listSharesRoute(shoppingListService, listShareService)
        createShareRoute(shoppingListService, listShareService)
        deleteShareRoute(shoppingListService, listShareService)
    }
}

private fun Route.listSharesRoute(
    shoppingListService: ShoppingListService,
    listShareService: ListShareService,
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

        val isOwner = transaction { list.owner?.id?.value == accountId }
        if (!isOwner) return@get call.respond(HttpStatusCode.Forbidden)

        val shares = listShareService.findByListId(listId)
        call.respond(shares.map { transaction { it.toResponse() } })
    }
}

private fun Route.createShareRoute(
    shoppingListService: ShoppingListService,
    listShareService: ListShareService,
) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        val list =
            shoppingListService.findById(listId)
                ?: return@post call.respond(HttpStatusCode.NotFound)

        val isOwner = transaction { list.owner?.id?.value == accountId }
        if (!isOwner) return@post call.respond(HttpStatusCode.Forbidden)

        val request = call.receive<CreateShareRequest>()
        val (permission, shareType) =
            parseShareRequest(request)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    shareRequestError(request),
                )

        val share =
            createShare(shareType, request, accountId, listId, permission, listShareService)
                ?: return@post Unit

        call.respond(HttpStatusCode.Created, transaction { share.toResponse() })
    }
}

private fun parseShareRequest(request: CreateShareRequest): Pair<SharePermission, ShareType>? {
    val permission = tryParseEnum<SharePermission>(request.permission)
    val shareType = tryParseEnum<ShareType>(request.type)
    return if (permission != null && shareType != null) permission to shareType else null
}

private fun shareRequestError(request: CreateShareRequest): Map<String, String> =
    when {
        tryParseEnum<SharePermission>(request.permission) == null ->
            mapOf("error" to "Invalid permission: ${request.permission}")
        else ->
            mapOf("error" to "Invalid share type: ${request.type}")
    }

private inline fun <reified T : Enum<T>> tryParseEnum(v: String): T? = runCatching { enumValueOf<T>(v) }.getOrNull()

private suspend fun io.ktor.server.routing.RoutingContext.createShare(
    shareType: ShareType,
    request: CreateShareRequest,
    accountId: java.util.UUID,
    listId: java.util.UUID,
    permission: SharePermission,
    listShareService: ListShareService,
): no.shoppinglist.domain.ListShare? =
    when (shareType) {
        ShareType.USER -> {
            val targetAccountId =
                request.accountId
                    ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            if (targetAccountId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "accountId is required for USER shares"))
                return null
            }
            if (targetAccountId == accountId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot share with yourself"))
                return null
            }
            listShareService.createUserShare(listId, targetAccountId, permission)
        }
        ShareType.LINK -> {
            if (request.expirationHours > 168) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Expiration must not exceed 168 hours (7 days)"),
                )
                return null
            }
            listShareService.createLinkShare(listId, permission, request.expirationHours)
        }
    }

private fun Route.deleteShareRoute(
    shoppingListService: ShoppingListService,
    listShareService: ListShareService,
) {
    delete("/{shareId}") {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val shareId =
            call.uuidParam("shareId")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val list =
            shoppingListService.findById(listId)
                ?: return@delete call.respond(HttpStatusCode.NotFound)

        val isOwner = transaction { list.owner?.id?.value == accountId }
        if (!isOwner) return@delete call.respond(HttpStatusCode.Forbidden)

        listShareService.delete(shareId)
        call.respond(HttpStatusCode.NoContent)
    }
}
