package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
        lateinit var listId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            accountId = UUID.randomUUID()
            listId = UUID.randomUUID()

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

                Account.new(accountId) {
                    email = "test@example.com"
                    displayName = "Test User"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                ShoppingList.new(listId) {
                    name = "Weekly Groceries"
                    owner = Account[accountId]
                    household = null
                    isPersonal = true
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

        test("can create weekly recurring item") {
            val recurringId = UUID.randomUUID()
            val nextOccurrence = LocalDate.now().plusDays(7)

            transaction(db) {
                RecurringItem.new(recurringId) {
                    list = ShoppingList[listId]
                    name = "Milk"
                    quantity = 2.0
                    unit = "liters"
                    frequency = RecurringFrequency.WEEKLY
                    this.nextOccurrence = nextOccurrence
                    isActive = true
                    createdBy = Account[accountId]
                }
            }

            transaction(db) {
                val recurring = RecurringItem.findById(recurringId)
                recurring.shouldNotBeNull()
                recurring.name shouldBe "Milk"
                recurring.frequency shouldBe RecurringFrequency.WEEKLY
                recurring.nextOccurrence shouldBe nextOccurrence
                recurring.isActive.shouldBeTrue()
            }
        }

        test("can pause recurring item") {
            val recurringId = UUID.randomUUID()

            transaction(db) {
                RecurringItem.new(recurringId) {
                    list = ShoppingList[listId]
                    name = "Bread"
                    quantity = 1.0
                    unit = null
                    frequency = RecurringFrequency.DAILY
                    nextOccurrence = LocalDate.now().plusDays(1)
                    isActive = true
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
    })
