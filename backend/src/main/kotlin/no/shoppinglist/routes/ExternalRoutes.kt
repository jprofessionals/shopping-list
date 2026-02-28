package no.shoppinglist.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.shoppinglist.service.ExternalItemRequest
import no.shoppinglist.service.ExternalListService

@Serializable
data class CreateExternalListRequest(
    val title: String? = null,
    val email: String? = null,
    val items: List<ExternalItemRequest> = emptyList(),
)

@Serializable
data class CreateExternalListResponse(
    val listId: String,
    val shareToken: String,
    val widgetUrl: String,
)

fun Route.externalRoutes(externalListService: ExternalListService) {
    route("/api/external") {
        post("/lists") {
            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
            call.response.header(HttpHeaders.AccessControlAllowMethods, "POST, GET, OPTIONS")
            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type")

            val request = call.receive<CreateExternalListRequest>()

            if (request.title.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "title is required"))
                return@post
            }

            val result = externalListService.createExternalList(
                title = request.title,
                email = request.email,
                items = request.items,
            )

            call.respond(
                HttpStatusCode.Created,
                CreateExternalListResponse(
                    listId = result.listId.toString(),
                    shareToken = result.shareToken,
                    widgetUrl = "/widget.js",
                ),
            )
        }

        options("/lists") {
            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
            call.response.header(HttpHeaders.AccessControlAllowMethods, "POST, GET, OPTIONS")
            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type")
            call.respond(HttpStatusCode.OK)
        }
    }
}
