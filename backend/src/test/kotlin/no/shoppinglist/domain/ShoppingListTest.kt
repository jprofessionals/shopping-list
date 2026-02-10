package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListTest :
    FunSpec({

        lateinit var db: Database
        lateinit var accountId: UUID
        lateinit var householdId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            accountId = UUID.randomUUID()
            householdId = UUID.randomUUID()

            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists)

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
            transaction(db) {
                SchemaUtils.drop(ShoppingLists, HouseholdMemberships, Households, Accounts)
            }
        }

        test("can create shopping list in household") {
            val listId = UUID.randomUUID()

            transaction(db) {
                ShoppingList.new(listId) {
                    name = "Groceries"
                    owner = Account[accountId]
                    household = Household[householdId]
                    isPersonal = false
                    createdAt = Instant.now()
                }
            }

            val list = transaction(db) { ShoppingList.findById(listId) }
            list.shouldNotBeNull()
            list.name shouldBe "Groceries"
            list.isPersonal.shouldBeFalse()
        }

        test("can create personal list without household") {
            val listId = UUID.randomUUID()

            transaction(db) {
                ShoppingList.new(listId) {
                    name = "Personal List"
                    owner = Account[accountId]
                    household = null
                    isPersonal = true
                    createdAt = Instant.now()
                }
            }

            transaction(db) {
                val list = ShoppingList.findById(listId)
                list.shouldNotBeNull()
                list.household.shouldBeNull()
            }
        }
    })
