package no.shoppinglist.routes.shoppinglist

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ItemHistoryService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.websocket.EventBroadcaster

fun Route.shoppingListRoutes(
    shoppingListService: ShoppingListService,
    listItemService: ListItemService,
    householdService: HouseholdService,
    listShareService: ListShareService,
    pinnedListService: PinnedListService,
    eventBroadcaster: EventBroadcaster,
    activityService: ActivityService,
    itemHistoryService: ItemHistoryService,
) {
    authenticate("auth-jwt") {
        route("/lists") {
            listCrudRoutes(shoppingListService, listItemService, householdService, pinnedListService)
            route("/{id}") {
                listPinningRoutes(shoppingListService, pinnedListService)
                listItemRoutes(
                    shoppingListService,
                    listItemService,
                    eventBroadcaster,
                    activityService,
                    itemHistoryService,
                )
                listSharingRoutes(shoppingListService, listShareService)
            }
        }
    }
}
