@file:Suppress("MatchingDeclarationName")

package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.ShoppingListService
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ActivityResponse(
    val type: String,
    val actorName: String,
    val targetName: String?,
    val timestamp: String,
)

fun Route.activityRoutes(
    activityService: ActivityService,
    shoppingListService: ShoppingListService,
) {
    authenticate("auth-jwt") {
        route("/lists/{id}/activity") {
            get {
                val accountId = call.getAccountId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val listId = call.uuidParam("id") ?: return@get call.respond(HttpStatusCode.BadRequest)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val permission = shoppingListService.getPermission(listId, accountId, null)
                if (permission == null) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val activities = activityService.getActivities(listId, limit)
                call.respond(mapOf("activity" to activities.toResponse()))
            }
        }
    }
}

private fun List<no.shoppinglist.domain.ListActivity>.toResponse() =
    map { activity ->
        transaction {
            ActivityResponse(
                type = activity.actionType,
                actorName = activity.account.displayName,
                targetName = activity.targetName,
                timestamp = activity.createdAt.toString(),
            )
        }
    }
