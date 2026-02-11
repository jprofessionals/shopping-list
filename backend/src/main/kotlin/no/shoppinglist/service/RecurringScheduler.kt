package no.shoppinglist.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.shoppinglist.config.RecurringConfig
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.RecurringItem
import no.shoppinglist.websocket.EventBroadcaster
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

        val householdIds = getAllHouseholdIds()

        for (householdId in householdIds) {
            processHousehold(householdId, today)
        }
    }

    private fun getAllHouseholdIds(): List<UUID> =
        transaction(db) {
            Household.all().map { it.id.value }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun processHousehold(
        householdId: UUID,
        today: LocalDate,
    ) {
        try {
            val activeItems = recurringItemService.findActiveByHousehold(householdId)
            val dueItems = activeItems.filter { isDue(it, today) }
            if (dueItems.isEmpty()) return

            val ownerId =
                transaction(db) {
                    dueItems
                        .first()
                        .createdBy.id.value
                }
            val listName = formatListName(today)
            val newList = shoppingListService.create(listName, ownerId, householdId, false)

            createItemsForList(newList.id.value, dueItems, ownerId)
            eventBroadcaster.broadcastListCreatedBySystem(newList, householdId)
            logger.info(
                "Created recurring list '{}' for household {} with {} items",
                listName,
                householdId,
                dueItems.size,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to process recurring items for household {}: {}",
                householdId,
                e.message,
            )
        }
    }

    private fun createItemsForList(
        listId: UUID,
        dueItems: List<RecurringItem>,
        ownerId: UUID,
    ) {
        for (item in dueItems) {
            val data =
                transaction(db) {
                    ItemCreationData(item.name, item.quantity, item.unit, item.id.value)
                }
            listItemService.createFromRecurring(
                listId,
                data.name,
                data.quantity,
                data.unit,
                ownerId,
                data.recurringItemId,
            )
        }
    }

    private fun isDue(
        item: RecurringItem,
        today: LocalDate,
    ): Boolean {
        val lastPurchased =
            transaction(db) { item.lastPurchased }
                ?: return true // Never purchased, always due

        val frequency = transaction(db) { item.frequency }
        val duration = config.getDuration(frequency)
        val daysSinceLastPurchase =
            java.time.temporal.ChronoUnit.DAYS
                .between(lastPurchased, today)

        return daysSinceLastPurchase >= duration.inWholeDays
    }

    private fun formatListName(date: LocalDate): String {
        val formatter =
            java.time.format.DateTimeFormatter
                .ofPattern("d. MMM yyyy")
        return "Handleliste ${date.format(formatter)}"
    }

    private data class ItemCreationData(
        val name: String,
        val quantity: Double,
        val unit: String?,
        val recurringItemId: UUID,
    )
}
