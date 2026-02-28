package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RecurringItemTest :
    FunSpec({

        lateinit var db: Database
        lateinit var accountId: UUID
        lateinit var householdId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            accountId = UUID.randomUUID()
            householdId = UUID.randomUUID()

            transaction(db) {
                SchemaUtils.create(
                    Accounts,
                    Households,
                    HouseholdMemberships,
                    ShoppingLists,
                    RecurringItems,
                    ListItems,
                    ListShares,
                )

                Account.new(accountId) {
                    email = "test@example.com"
                    displayName = "Test User"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                Household.new(householdId) {
                    name = "Test Household"
                    createdAt = Instant.now()
                }
            }
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        test("can create weekly recurring item") {
            val recurringId = UUID.randomUUID()

            transaction(db) {
                RecurringItem.new(recurringId) {
                    household = Household[householdId]
                    name = "Milk"
                    quantity = 2.0
                    unit = "liters"
                    frequency = RecurringFrequency.WEEKLY
                    lastPurchased = null
                    isActive = true
                    pausedUntil = null
                    createdBy = Account[accountId]
                }
            }

            transaction(db) {
                val recurring = RecurringItem.findById(recurringId)
                recurring.shouldNotBeNull()
                recurring.name shouldBe "Milk"
                recurring.frequency shouldBe RecurringFrequency.WEEKLY
                recurring.lastPurchased.shouldBeNull()
                recurring.isActive.shouldBeTrue()
                recurring.pausedUntil.shouldBeNull()
            }
        }

        test("can pause recurring item") {
            val recurringId = UUID.randomUUID()

            transaction(db) {
                RecurringItem.new(recurringId) {
                    household = Household[householdId]
                    name = "Bread"
                    quantity = 1.0
                    unit = null
                    frequency = RecurringFrequency.DAILY
                    lastPurchased = null
                    isActive = true
                    pausedUntil = null
                    createdBy = Account[accountId]
                }
            }

            transaction(db) {
                val recurring = RecurringItem[recurringId]
                recurring.isActive = false
            }

            transaction(db) {
                val recurring = RecurringItem.findById(recurringId)
                recurring.shouldNotBeNull()
                recurring.isActive.shouldBeFalse()
            }
        }

        test("can set pausedUntil date") {
            val recurringId = UUID.randomUUID()
            val pauseDate = LocalDate.now().plusDays(14)

            transaction(db) {
                RecurringItem.new(recurringId) {
                    household = Household[householdId]
                    name = "Cheese"
                    quantity = 1.0
                    unit = null
                    frequency = RecurringFrequency.MONTHLY
                    lastPurchased = null
                    isActive = false
                    pausedUntil = pauseDate
                    createdBy = Account[accountId]
                }
            }

            transaction(db) {
                val recurring = RecurringItem.findById(recurringId)
                recurring.shouldNotBeNull()
                recurring.isActive.shouldBeFalse()
                recurring.pausedUntil shouldBe pauseDate
            }
        }

        test("can update lastPurchased") {
            val recurringId = UUID.randomUUID()
            val today = LocalDate.now()

            transaction(db) {
                RecurringItem.new(recurringId) {
                    household = Household[householdId]
                    name = "Juice"
                    quantity = 1.0
                    unit = "liter"
                    frequency = RecurringFrequency.WEEKLY
                    lastPurchased = null
                    isActive = true
                    pausedUntil = null
                    createdBy = Account[accountId]
                }
            }

            transaction(db) {
                val recurring = RecurringItem[recurringId]
                recurring.lastPurchased = today
            }

            transaction(db) {
                val recurring = RecurringItem.findById(recurringId)
                recurring.shouldNotBeNull()
                recurring.lastPurchased shouldBe today
            }
        }
    })
