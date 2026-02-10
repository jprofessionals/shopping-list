package no.shoppinglist.routes.comment

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.CommentService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.websocket.EventBroadcaster
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.listCommentRoutes(
    commentService: CommentService,
    shoppingListService: ShoppingListService,
    eventBroadcaster: EventBroadcaster,
) {
    authenticate("auth-jwt") {
        route("/lists/{id}/comments") {
            listCommentsGet(commentService, shoppingListService)
            listCommentsCreate(commentService, shoppingListService, eventBroadcaster)
            route("/{commentId}") {
                listCommentUpdate(commentService, eventBroadcaster)
                listCommentDelete(commentService, eventBroadcaster)
            }
        }
    }
}

private fun Route.listCommentsGet(
    commentService: CommentService,
    shoppingListService: ShoppingListService,
) {
    get {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@get call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) == null) {
            return@get call.respond(HttpStatusCode.Forbidden)
        }

        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
        val comments = commentService.findByTarget(CommentTargetType.LIST, listId, limit, offset)
        call.respond(transaction { comments.map { it.toResponse() } })
    }
}

private fun Route.listCommentsCreate(
    commentService: CommentService,
    shoppingListService: ShoppingListService,
    eventBroadcaster: EventBroadcaster,
) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val listId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (shoppingListService.getPermission(listId, accountId, null) == null) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<CreateCommentRequest>()
        if (request.text.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
        }

        val comment = commentService.create(CommentTargetType.LIST, listId, accountId, request.text)
        eventBroadcaster.broadcastCommentAdded(comment, accountId)
        call.respond(HttpStatusCode.Created, transaction { comment.toResponse() })
    }
}

private fun Route.listCommentUpdate(
    commentService: CommentService,
    eventBroadcaster: EventBroadcaster,
) {
    patch {
        val accountId =
            call.getAccountId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val commentId =
            call.uuidParam("commentId")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

        val request = call.receive<UpdateCommentRequest>()
        if (request.text.isBlank()) {
            return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
        }

        val updated =
            commentService.update(commentId, accountId, request.text)
                ?: return@patch call.respond(HttpStatusCode.NotFound)

        eventBroadcaster.broadcastCommentUpdated(updated, accountId)
        call.respond(transaction { updated.toResponse() })
    }
}

private fun Route.listCommentDelete(
    commentService: CommentService,
    eventBroadcaster: EventBroadcaster,
) {
    delete {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val commentId =
            call.uuidParam("commentId")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val comment =
            commentService.findById(commentId)
                ?: return@delete call.respond(HttpStatusCode.NotFound)

        val (targetType, targetId) = transaction { comment.targetType to comment.targetId }

        val deleted = commentService.delete(commentId, accountId)
        if (!deleted) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        eventBroadcaster.broadcastCommentDeleted(targetType, targetId, commentId, accountId)
        call.respond(HttpStatusCode.NoContent)
    }
}
