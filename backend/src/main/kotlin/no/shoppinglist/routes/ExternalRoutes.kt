package no.shoppinglist.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.config.InMemoryRateLimiter
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

fun Route.externalRoutes(
    externalListService: ExternalListService,
    rateLimiter: InMemoryRateLimiter? = null,
) {
    route("/api/external") {
        post("/lists") {
            handleCreateList(externalListService, rateLimiter)
        }

        options("/lists") {
            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
            call.response.header(HttpHeaders.AccessControlAllowMethods, "POST, GET, OPTIONS")
            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type")
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.handleCreateList(
    externalListService: ExternalListService,
    rateLimiter: InMemoryRateLimiter?,
) {
    call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    call.response.header(HttpHeaders.AccessControlAllowMethods, "POST, GET, OPTIONS")
    call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type")

    if (rateLimiter != null) {
        val clientIp = call.request.origin.remoteAddress
        if (!rateLimiter.tryAcquire(clientIp)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
            return
        }
    }

    val request = call.receive<CreateExternalListRequest>()

    if (request.title.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "title is required"))
        return
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
