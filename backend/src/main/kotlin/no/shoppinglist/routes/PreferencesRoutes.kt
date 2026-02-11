package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.PreferencesData
import no.shoppinglist.service.PreferencesService

@Serializable
data class PreferencesResponse(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
    val notifyNewList: Boolean,
    val notifyItemAdded: Boolean,
    val notifyNewComment: Boolean,
)

@Serializable
data class UpdatePreferencesRequest(
    val smartParsingEnabled: Boolean? = null,
    val defaultQuantity: Double? = null,
    val theme: String? = null,
    val notifyNewList: Boolean? = null,
    val notifyItemAdded: Boolean? = null,
    val notifyNewComment: Boolean? = null,
)

fun Route.preferencesRoutes(preferencesService: PreferencesService) {
    authenticate("auth-jwt") {
        route("/preferences") {
            get {
                val accountId = call.getAccountId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val prefs = preferencesService.getPreferences(accountId)
                call.respond(prefs.toResponse())
            }

            patch {
                val accountId = call.getAccountId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdatePreferencesRequest>()
                val prefs =
                    preferencesService.updatePreferences(
                        accountId = accountId,
                        smartParsingEnabled = request.smartParsingEnabled,
                        defaultQuantity = request.defaultQuantity,
                        theme = request.theme,
                        notifyNewList = request.notifyNewList,
                        notifyItemAdded = request.notifyItemAdded,
                        notifyNewComment = request.notifyNewComment,
                    )
                call.respond(prefs.toResponse())
            }
        }
    }
}

private fun PreferencesData.toResponse() =
    PreferencesResponse(
        smartParsingEnabled = smartParsingEnabled,
        defaultQuantity = defaultQuantity,
        theme = theme,
        notifyNewList = notifyNewList,
        notifyItemAdded = notifyItemAdded,
        notifyNewComment = notifyNewComment,
    )
