@file:Suppress("MatchingDeclarationName")

package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.service.ItemHistoryService
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class SuggestionResponse(
    val name: String,
    val typicalQuantity: Double,
    val typicalUnit: String?,
    val useCount: Int,
)

fun Route.suggestionRoutes(itemHistoryService: ItemHistoryService) {
    authenticate("auth-jwt") {
        route("/items/suggestions") {
            get {
                val accountId =
                    call
                        .principal<JWTPrincipal>()
                        ?.subject
                        ?.let { UUID.fromString(it) }
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val query = call.request.queryParameters["q"] ?: ""
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

                val suggestions = itemHistoryService.searchSuggestions(accountId, query, limit)
                val response =
                    suggestions.map { item ->
                        transaction {
                            SuggestionResponse(
                                name = item.displayName,
                                typicalQuantity = item.typicalQuantity,
                                typicalUnit = item.typicalUnit,
                                useCount = item.useCount,
                            )
                        }
                    }

                call.respond(mapOf("suggestions" to response))
            }
        }
    }
}
