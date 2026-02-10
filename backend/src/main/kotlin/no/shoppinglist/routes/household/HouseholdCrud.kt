package no.shoppinglist.routes.household

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.HouseholdService
import org.jetbrains.exposed.sql.transactions.transaction

internal fun Route.householdCrudRoutes(householdService: HouseholdService) {
    listHouseholdsRoute(householdService)
    createHouseholdRoute(householdService)
    route("/{id}") {
        getHouseholdRoute(householdService)
        updateHouseholdRoute(householdService)
        deleteHouseholdRoute(householdService)
    }
}

private fun Route.listHouseholdsRoute(householdService: HouseholdService) {
    get {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val households = householdService.findByAccountId(accountId)
        val response =
            households.map { household ->
                val members = householdService.getMembers(household.id.value)
                val isOwner = householdService.isOwner(household.id.value, accountId)
                transaction {
                    HouseholdResponse(
                        id = household.id.value.toString(),
                        name = household.name,
                        createdAt = household.createdAt.toString(),
                        memberCount = members.size,
                        isOwner = isOwner,
                    )
                }
            }
        call.respond(response)
    }
}

private fun Route.createHouseholdRoute(householdService: HouseholdService) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val request = call.receive<CreateHouseholdRequest>()
        val household = householdService.create(request.name, accountId)

        call.respond(
            HttpStatusCode.Created,
            transaction {
                HouseholdResponse(
                    id = household.id.value.toString(),
                    name = household.name,
                    createdAt = household.createdAt.toString(),
                    memberCount = 1,
                    isOwner = true,
                )
            },
        )
    }
}

private fun Route.getHouseholdRoute(householdService: HouseholdService) {
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

        val household =
            householdService.findById(householdId)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        val members = householdService.getMembers(householdId)
        call.respond(
            transaction {
                HouseholdDetailResponse(
                    id = household.id.value.toString(),
                    name = household.name,
                    createdAt = household.createdAt.toString(),
                    members = members.map { it.toResponse() },
                )
            },
        )
    }
}

private fun Route.updateHouseholdRoute(householdService: HouseholdService) {
    patch {
        val accountId =
            call.getAccountId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isMember(householdId, accountId)) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<UpdateHouseholdRequest>()
        val updated =
            householdService.update(householdId, request.name)
                ?: return@patch call.respond(HttpStatusCode.NotFound)

        val members = householdService.getMembers(householdId)
        val isOwner = householdService.isOwner(householdId, accountId)

        call.respond(
            transaction {
                HouseholdResponse(
                    id = updated.id.value.toString(),
                    name = updated.name,
                    createdAt = updated.createdAt.toString(),
                    memberCount = members.size,
                    isOwner = isOwner,
                )
            },
        )
    }
}

private fun Route.deleteHouseholdRoute(householdService: HouseholdService) {
    delete {
        val accountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isOwner(householdId, accountId)) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        householdService.delete(householdId)
        call.respond(HttpStatusCode.NoContent)
    }
}
