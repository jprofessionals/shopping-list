package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.RecurringConfig
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.config.ValkeyConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.HouseholdMembership
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.domain.RecurringFrequency
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.websocket.EventBroadcaster
import no.shoppinglist.websocket.WebSocketBroadcastService
import no.shoppinglist.websocket.WebSocketSessionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class RecurringSchedulerTest :
    FunSpec({
        lateinit var db: Database
        lateinit var recurringItemService: RecurringItemService
        lateinit var shoppingListService: ShoppingListService
        lateinit var listItemService: ListItemService
        lateinit var scheduler: RecurringScheduler
        lateinit var testAccountId: UUID
        lateinit var testHouseholdId: UUID

        val testConfig =
            RecurringConfig(
                schedulerInterval = 1.seconds,
                schedulerInitialDelay = 0.seconds,
                frequencies =
                    mapOf(
                        RecurringFrequency.DAILY to 1.days,
                        RecurringFrequency.WEEKLY to 7.days,
                        RecurringFrequency.BIWEEKLY to 14.days,
                        RecurringFrequency.MONTHLY to 30.days,
                    ),
            )

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(
                    Accounts,
                    Households,
                    HouseholdMemberships,
                    ShoppingLists,
                    ListItems,
                    ListShares,
                    RecurringItems,
                )
            }
            recurringItemService = RecurringItemService(db)
            shoppingListService = ShoppingListService(db)
            listItemService = ListItemService(db)
        }

        beforeTest {
            testAccountId = UUID.randomUUID()
            testHouseholdId = UUID.randomUUID()

            transaction(db) {
                Account.new(testAccountId) {
                    email = "test-${UUID.randomUUID()}@example.com"
                    displayName = "Test User"
                    createdAt = Instant.now()
                }
                Household.new(testHouseholdId) {
                    name = "Test Household"
                    createdAt = Instant.now()
                }
                HouseholdMembership.new {
                    account = Account[testAccountId]
                    household = Household[testHouseholdId]
                    role = MembershipRole.OWNER
                    joinedAt = Instant.now()
                }
            }

            // Use real instances with dummy Valkey config (silently fails to connect, which is fine)
            val valkeyService = ValkeyService(ValkeyConfig(host = "localhost", port = 6379, password = ""))
            val sessionManager = WebSocketSessionManager()
            val broadcastService = WebSocketBroadcastService(valkeyService, sessionManager)
            val eventBroadcaster = EventBroadcaster(broadcastService)

            scheduler =
                RecurringScheduler(
                    config = testConfig,
                    recurringItemService = recurringItemService,
                    shoppingListService = shoppingListService,
                    listItemService = listItemService,
                    eventBroadcaster = eventBroadcaster,
                    db = db,
                )
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        test("creates list when items are due (never purchased)") {
            recurringItemService.create(
                testHouseholdId,
                testAccountId,
                "Milk",
                2.0,
                "liters",
                RecurringFrequency.WEEKLY,
            )
            recurringItemService.create(
                testHouseholdId,
                testAccountId,
                "Bread",
                1.0,
                null,
                RecurringFrequency.DAILY,
            )

            scheduler.runSchedulerCycle()

            val lists =
                transaction(db) {
                    ShoppingList.find { ShoppingLists.household eq testHouseholdId }.toList()
                }
            lists shouldHaveSize 1

            val items = listItemService.findByListId(lists.first().id.value)
            items shouldHaveSize 2
        }

        test("does not create list when no items are due") {
            val item =
                recurringItemService.create(
                    testHouseholdId,
                    testAccountId,
                    "Fresh Milk",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            recurringItemService.markPurchased(item.id.value, LocalDate.now())

            scheduler.runSchedulerCycle()

            val lists =
                transaction(db) {
                    ShoppingList.find { ShoppingLists.household eq testHouseholdId }.toList()
                }
            lists shouldHaveSize 0
        }

        test("creates list when item frequency has elapsed") {
            val item =
                recurringItemService.create(
                    testHouseholdId,
                    testAccountId,
                    "Old Milk",
                    1.0,
                    null,
                    RecurringFrequency.DAILY,
                )
            recurringItemService.markPurchased(item.id.value, LocalDate.now().minusDays(2))

            scheduler.runSchedulerCycle()

            val lists =
                transaction(db) {
                    ShoppingList.find { ShoppingLists.household eq testHouseholdId }.toList()
                }
            lists shouldHaveSize 1
        }

        test("skips paused items") {
            val item =
                recurringItemService.create(
                    testHouseholdId,
                    testAccountId,
                    "Paused Item",
                    1.0,
                    null,
                    RecurringFrequency.DAILY,
                )
            recurringItemService.pause(item.id.value, null)

            scheduler.runSchedulerCycle()

            val lists =
                transaction(db) {
                    ShoppingList.find { ShoppingLists.household eq testHouseholdId }.toList()
                }
            lists shouldHaveSize 0
        }

        test("generated items have recurringItem reference") {
            recurringItemService.create(
                testHouseholdId,
                testAccountId,
                "Tracked Milk",
                1.0,
                null,
                RecurringFrequency.DAILY,
            )

            scheduler.runSchedulerCycle()

            val lists =
                transaction(db) {
                    ShoppingList.find { ShoppingLists.household eq testHouseholdId }.toList()
                }
            val items = listItemService.findByListId(lists.first().id.value)
            items shouldHaveSize 1

            transaction(db) {
                items.first().recurringItem.shouldNotBeNull()
                items.first().name shouldBe "Tracked Milk"
            }
        }
    })
