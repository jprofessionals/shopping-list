package no.shoppinglist.routes.household

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.routes.uuidParam
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.HouseholdService

internal fun Route.householdMemberRoutes(
    householdService: HouseholdService,
    accountService: AccountService,
) {
    route("/members") {
        addMemberRoute(householdService, accountService)
        route("/{accountId}") {
            updateMemberRoleRoute(householdService)
            removeMemberRoute(householdService)
        }
    }
}

private fun Route.addMemberRoute(
    householdService: HouseholdService,
    accountService: AccountService,
) {
    post {
        val accountId =
            call.getAccountId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isOwner(householdId, accountId)) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<AddMemberRequest>()
        val targetAccount =
            accountService.findByEmail(request.email)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

        val role =
            runCatching { MembershipRole.valueOf(request.role) }
                .getOrElse { MembershipRole.MEMBER }

        val membership = householdService.addMember(householdId, targetAccount.id.value, role)
        if (membership == null) {
            return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "User is already a member"))
        }

        call.respond(HttpStatusCode.Created, mapOf("message" to "Member added"))
    }
}

private fun Route.updateMemberRoleRoute(householdService: HouseholdService) {
    patch {
        val currentAccountId =
            call.getAccountId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)
        val targetAccountId =
            call.uuidParam("accountId")
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isOwner(householdId, currentAccountId)) {
            return@patch call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<UpdateMemberRoleRequest>()
        val newRole =
            runCatching { MembershipRole.valueOf(request.role) }
                .getOrElse { return@patch call.respond(HttpStatusCode.BadRequest) }

        val updated = householdService.updateMemberRole(householdId, targetAccountId, newRole)
        if (!updated) {
            return@patch call.respond(HttpStatusCode.NotFound)
        }

        call.respond(HttpStatusCode.OK, mapOf("message" to "Role updated"))
    }
}

private fun Route.removeMemberRoute(householdService: HouseholdService) {
    delete {
        val currentAccountId =
            call.getAccountId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val householdId =
            call.uuidParam("id")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val targetAccountId =
            call.uuidParam("accountId")
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

        val isOwner = householdService.isOwner(householdId, currentAccountId)
        val isSelf = currentAccountId == targetAccountId

        if (!isOwner && !isSelf) {
            return@delete call.respond(HttpStatusCode.Forbidden)
        }

        val removed = householdService.removeMember(householdId, targetAccountId)
        if (!removed) {
            return@delete call.respond(HttpStatusCode.NotFound)
        }

        call.respond(HttpStatusCode.NoContent)
    }
}
