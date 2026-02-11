package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.RecurringFrequency
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RecurringItemServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var service: RecurringItemService
        lateinit var testAccountId: UUID
        lateinit var testHouseholdId: UUID

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
            service = RecurringItemService(db)
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
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(
                    RecurringItems,
                    ListShares,
                    ListItems,
                    ShoppingLists,
                    HouseholdMemberships,
                    Households,
                    Accounts,
                )
            }
        }

        test("create adds recurring item to household") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Milk",
                    2.0,
                    "liters",
                    RecurringFrequency.WEEKLY,
                )

            transaction(db) {
                item.name shouldBe "Milk"
                item.quantity shouldBe 2.0
                item.unit shouldBe "liters"
                item.frequency shouldBe RecurringFrequency.WEEKLY
                item.lastPurchased.shouldBeNull()
                item.isActive.shouldBeTrue()
                item.pausedUntil.shouldBeNull()
            }
        }

        test("getByHousehold returns all items for household") {
            service.create(testHouseholdId, testAccountId, "Milk", 1.0, null, RecurringFrequency.WEEKLY)
            service.create(testHouseholdId, testAccountId, "Bread", 2.0, null, RecurringFrequency.DAILY)

            val items = service.getByHousehold(testHouseholdId)
            items shouldHaveSize 2
        }

        test("update changes item properties") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Milk",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            val itemId = item.id.value

            val updated = service.update(itemId, "Whole Milk", 2.0, "liters", RecurringFrequency.BIWEEKLY)
            updated.shouldNotBeNull()

            transaction(db) {
                updated.name shouldBe "Whole Milk"
                updated.quantity shouldBe 2.0
                updated.unit shouldBe "liters"
                updated.frequency shouldBe RecurringFrequency.BIWEEKLY
            }
        }

        test("delete removes item") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Bread",
                    1.0,
                    null,
                    RecurringFrequency.DAILY,
                )
            val itemId = item.id.value

            service.delete(itemId).shouldBeTrue()
            service.findById(itemId).shouldBeNull()
        }

        test("pause sets isActive to false") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Eggs",
                    12.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            val itemId = item.id.value

            val paused = service.pause(itemId, null)
            paused.shouldNotBeNull()
            transaction(db) {
                paused.isActive.shouldBeFalse()
                paused.pausedUntil.shouldBeNull()
            }
        }

        test("pause with date sets pausedUntil") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Butter",
                    1.0,
                    null,
                    RecurringFrequency.MONTHLY,
                )
            val itemId = item.id.value
            val until = LocalDate.now().plusDays(14)

            val paused = service.pause(itemId, until)
            paused.shouldNotBeNull()
            transaction(db) {
                paused.isActive.shouldBeFalse()
                paused.pausedUntil shouldBe until
            }
        }

        test("resume sets isActive to true and clears pausedUntil") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Cheese",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            val itemId = item.id.value

            service.pause(itemId, LocalDate.now().plusDays(7))
            val resumed = service.resume(itemId)
            resumed.shouldNotBeNull()
            transaction(db) {
                resumed.isActive.shouldBeTrue()
                resumed.pausedUntil.shouldBeNull()
            }
        }

        test("markPurchased updates lastPurchased") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Juice",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            val itemId = item.id.value
            val today = LocalDate.now()

            val marked = service.markPurchased(itemId, today)
            marked.shouldNotBeNull()
            transaction(db) {
                marked.lastPurchased shouldBe today
            }
        }

        test("findActiveByHousehold excludes paused items") {
            service.create(testHouseholdId, testAccountId, "Active Item", 1.0, null, RecurringFrequency.DAILY)
            val pausedItem =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Paused Item",
                    1.0,
                    null,
                    RecurringFrequency.DAILY,
                )
            service.pause(pausedItem.id.value, null)

            val active = service.findActiveByHousehold(testHouseholdId)
            active shouldHaveSize 1
            transaction(db) {
                active.first().name shouldBe "Active Item"
            }
        }

        test("reactivateExpiredPauses reactivates items with past pausedUntil") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Expired Pause",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            service.pause(item.id.value, LocalDate.now().minusDays(1))

            val reactivated = service.reactivateExpiredPauses()
            reactivated shouldBe 1

            val found = service.findById(item.id.value)
            found.shouldNotBeNull()
            transaction(db) {
                found.isActive.shouldBeTrue()
                found.pausedUntil.shouldBeNull()
            }
        }

        test("reactivateExpiredPauses does not reactivate future pauses") {
            val item =
                service.create(
                    testHouseholdId,
                    testAccountId,
                    "Future Pause",
                    1.0,
                    null,
                    RecurringFrequency.WEEKLY,
                )
            service.pause(item.id.value, LocalDate.now().plusDays(7))

            val reactivated = service.reactivateExpiredPauses()
            reactivated shouldBe 0
        }
    })
