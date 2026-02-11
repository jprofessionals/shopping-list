package no.shoppinglist.routes.household

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.RecurringItemService

fun Route.householdRoutes(
    householdService: HouseholdService,
    accountService: AccountService,
    recurringItemService: RecurringItemService,
) {
    authenticate("auth-jwt") {
        route("/households") {
            householdCrudRoutes(householdService)
            route("/{id}") {
                householdMemberRoutes(householdService, accountService)
                recurringItemRoutes(householdService, recurringItemService)
            }
        }
    }
}
