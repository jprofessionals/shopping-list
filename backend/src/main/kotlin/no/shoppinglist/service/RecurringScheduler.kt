package no.shoppinglist.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.shoppinglist.config.RecurringConfig
import no.shoppinglist.domain.HouseholdMembership
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.domain.RecurringFrequency
import no.shoppinglist.domain.RecurringItem
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.websocket.EventBroadcaster
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class RecurringScheduler(
    private val config: RecurringConfig,
    private val recurringItemService: RecurringItemService,
    private val shoppingListService: ShoppingListService,
    private val listItemService: ListItemService,
    private val eventBroadcaster: EventBroadcaster,
    private val db: org.jetbrains.exposed.sql.Database,
) {
    private val logger = LoggerFactory.getLogger(RecurringScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        job =
            scope.launch {
                delay(config.schedulerInitialDelay)
                logger.info(
                    "Recurring scheduler started (interval={})",
                    config.schedulerInterval,
                )

                while (isActive) {
                    runSchedulerCycle()
                    delay(config.schedulerInterval)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        logger.info("Recurring scheduler stopped")
    }

    internal fun runSchedulerCycle() {
        val today = LocalDate.now()

        val reactivated = recurringItemService.reactivateExpiredPauses(today)
        if (reactivated > 0) {
            logger.info("Reactivated {} paused recurring items", reactivated)
        }

        val householdIds = getHouseholdIdsWithActiveItems()

        for (householdId in householdIds) {
            processHousehold(householdId, today)
        }
    }

    private fun getHouseholdIdsWithActiveItems(): List<UUID> =
        transaction(db) {
            RecurringItems
                .select(RecurringItems.household)
                .where { RecurringItems.isActive eq true }
                .withDistinct()
                .map { it[RecurringItems.household].value }
        }

    private fun getHouseholdOwnerId(householdId: UUID): UUID =
        transaction(db) {
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.role eq MembershipRole.OWNER)
                }.first()
                .account.id.value
        }

    @Suppress("TooGenericExceptionCaught")
    private fun processHousehold(
        householdId: UUID,
        today: LocalDate,
    ) {
        try {
            val itemData =
                transaction(db) {
                    RecurringItem
                        .find {
                            (RecurringItems.household eq householdId) and
                                (RecurringItems.isActive eq true)
                        }.map { it.toData() }
                }
            val dueItems = itemData.filter { isDue(it, today) }
            if (dueItems.isEmpty()) return

            val ownerId = getHouseholdOwnerId(householdId)
            val listName = formatListName(today)
            val newList = shoppingListService.create(listName, ownerId, householdId, false)
            createListItems(newList.id.value, dueItems, ownerId)
            eventBroadcaster.broadcastListCreatedBySystem(newList, householdId)
            logger.info("Created recurring list '{}' with {} items", listName, dueItems.size)
        } catch (e: Exception) {
            logger.error("Recurring items failed for household {}: {}", householdId, e.message)
        }
    }

    private fun createListItems(
        listId: UUID,
        items: List<RecurringItemData>,
        ownerId: UUID,
    ) {
        for (item in items) {
            listItemService.createFromRecurring(
                listId,
                item.name,
                item.quantity,
                item.unit,
                ownerId,
                item.id,
            )
        }
    }

    private fun isDue(
        item: RecurringItemData,
        today: LocalDate,
    ): Boolean {
        val lastPurchased = item.lastPurchased ?: return true
        val duration = config.getDuration(item.frequency)
        val daysSinceLastPurchase =
            java.time.temporal.ChronoUnit.DAYS
                .between(lastPurchased, today)
        return daysSinceLastPurchase >= duration.inWholeDays
    }

    private fun formatListName(date: LocalDate): String = date.toString()

    private fun RecurringItem.toData() =
        RecurringItemData(
            id = id.value,
            name = name,
            quantity = quantity,
            unit = unit,
            frequency = frequency,
            lastPurchased = lastPurchased,
        )
}

internal data class RecurringItemData(
    val id: UUID,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val frequency: RecurringFrequency,
    val lastPurchased: LocalDate?,
)
