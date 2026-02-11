package no.shoppinglist.routes.household

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.domain.RecurringFrequency
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.RecurringItemService
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

private const val NAME_MIN_LENGTH = 1
private const val NAME_MAX_LENGTH = 255

private data class AuthContext(
    val accountId: UUID,
    val householdId: UUID,
)

internal fun Route.recurringItemRoutes(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    route("/recurring-items") {
        listRecurringItems(householdService, recurringItemService)
        createRecurringItem(householdService, recurringItemService)
        route("/{itemId}") {
            updateRecurringItem(householdService, recurringItemService)
            deleteRecurringItem(householdService, recurringItemService)
            post("/pause") {
                pauseRecurringItem(householdService, recurringItemService)
            }
            post("/resume") {
                resumeRecurringItem(householdService, recurringItemService)
            }
        }
    }
}

private fun Route.listRecurringItems(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    get {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@get call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isMember(householdId, accountId)) {
            return@get call.respond(HttpStatusCode.Forbidden)
        }

        val items = recurringItemService.getByHousehold(householdId)
        call.respond(items.map { transaction { it.toResponse() } })
    }
}

private fun Route.createRecurringItem(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    post {
        val auth =
            resolveAuth(householdService)
                ?: return@post
        val request = call.receive<CreateRecurringItemRequest>()
        validateNameOrRespond(request.name) ?: return@post
        val frequency =
            parseFrequencyOrRespond(request.frequency)
                ?: return@post

        val item =
            recurringItemService.create(
                auth.householdId,
                auth.accountId,
                request.name,
                request.quantity,
                request.unit,
                frequency,
            )
        call.respond(HttpStatusCode.Created, transaction { item.toResponse() })
    }
}

private fun Route.updateRecurringItem(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    patch {
        val auth =
            resolveAuth(householdService)
                ?: return@patch
        val itemId =
            call.uuidParam("itemId")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<UpdateRecurringItemRequest>()
        validateNameOrRespond(request.name) ?: return@patch
        val frequency =
            parseFrequencyOrRespond(request.frequency)
                ?: return@patch

        val updated =
            recurringItemService.update(
                itemId,
                request.name,
                request.quantity,
                request.unit,
                frequency,
            ) ?: return@patch call.respond(HttpStatusCode.NotFound)

        call.respond(transaction { updated.toResponse() })
    }
}

private fun Route.deleteRecurringItem(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    delete {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val itemId =
            call.uuidParam("itemId")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isMember(householdId, accountId)) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        recurringItemService.delete(itemId)
        call.respond(HttpStatusCode.NoContent)
    }
}

@Suppress("ReturnCount")
private suspend fun RoutingContext.pauseRecurringItem(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    val accountId =
        call.getAccountId()
            ?: return call.respond(HttpStatusCode.Unauthorized)
    val householdId =
        call.uuidParam("id")
            ?: return call.respond(HttpStatusCode.BadRequest)
    val itemId =
        call.uuidParam("itemId")
            ?: return call.respond(HttpStatusCode.BadRequest)

    if (!householdService.isMember(householdId, accountId)) {
        return call.respond(HttpStatusCode.Forbidden)
    }

    val request = call.receive<PauseRecurringItemRequest>()
    val until = request.until?.let { LocalDate.parse(it) }

    val paused =
        recurringItemService.pause(itemId, until)
            ?: return call.respond(HttpStatusCode.NotFound)

    call.respond(transaction { paused.toResponse() })
}

@Suppress("ReturnCount")
private suspend fun RoutingContext.resumeRecurringItem(
    householdService: HouseholdService,
    recurringItemService: RecurringItemService,
) {
    val accountId =
        call.getAccountId()
            ?: return call.respond(HttpStatusCode.Unauthorized)
    val householdId =
        call.uuidParam("id")
            ?: return call.respond(HttpStatusCode.BadRequest)
    val itemId =
        call.uuidParam("itemId")
            ?: return call.respond(HttpStatusCode.BadRequest)

    if (!householdService.isMember(householdId, accountId)) {
        return call.respond(HttpStatusCode.Forbidden)
    }

    val resumed =
        recurringItemService.resume(itemId)
            ?: return call.respond(HttpStatusCode.NotFound)

    call.respond(transaction { resumed.toResponse() })
}

@Suppress("ReturnCount")
private suspend fun RoutingContext.resolveAuth(householdService: HouseholdService): AuthContext? {
    val accountId =
        call.getAccountId()
            ?: return null.also { call.respond(HttpStatusCode.Unauthorized) }
    val householdId =
        call.uuidParam("id")
            ?: return null.also { call.respond(HttpStatusCode.BadRequest) }
    if (!householdService.isMember(householdId, accountId)) {
        call.respond(HttpStatusCode.Forbidden)
        return null
    }
    return AuthContext(accountId, householdId)
}

private suspend fun RoutingContext.validateNameOrRespond(name: String): Unit? {
    if (name.length !in NAME_MIN_LENGTH..NAME_MAX_LENGTH) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Name must be between $NAME_MIN_LENGTH and $NAME_MAX_LENGTH characters"),
        )
        return null
    }
    return Unit
}

private suspend fun RoutingContext.parseFrequencyOrRespond(value: String): RecurringFrequency? {
    val frequency = parseFrequency(value)
    if (frequency == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Invalid frequency: $value"),
        )
    }
    return frequency
}

private fun parseFrequency(value: String): RecurringFrequency? =
    try {
        RecurringFrequency.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }
