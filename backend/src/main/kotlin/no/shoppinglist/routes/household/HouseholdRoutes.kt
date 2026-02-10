package no.shoppinglist.routes.household

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.HouseholdService

fun Route.householdRoutes(
    householdService: HouseholdService,
    accountService: AccountService,
) {
    authenticate("auth-jwt") {
        route("/households") {
            householdCrudRoutes(householdService)
            route("/{id}") {
                householdMemberRoutes(householdService, accountService)
            }
        }
    }
}
