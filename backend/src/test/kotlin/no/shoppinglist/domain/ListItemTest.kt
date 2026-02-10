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
import java.util.UUID

class ListItemTest :
    FunSpec({

        lateinit var db: Database
        lateinit var accountId: UUID
        lateinit var listId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            accountId = UUID.randomUUID()
            listId = UUID.randomUUID()

            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems)

                Account.new(accountId) {
                    email = "test@example.com"
                    displayName = "Test User"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                ShoppingList.new(listId) {
                    name = "Test List"
                    owner = Account[accountId]
                    household = null
                    isPersonal = true
                    createdAt = Instant.now()
                }
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
            }
        }

        test("can create list item with quantity") {
            val itemId = UUID.randomUUID()

            transaction(db) {
                ListItem.new(itemId) {
                    list = ShoppingList[listId]
                    name = "Milk"
                    quantity = 2.0
                    unit = "liters"
                    barcode = null
                    isChecked = false
                    checkedBy = null
                    createdBy = Account[accountId]
                    createdAt = Instant.now()
                    updatedAt = Instant.now()
                }
            }

            transaction(db) {
                val item = ListItem.findById(itemId)
                item.shouldNotBeNull()
                item.name shouldBe "Milk"
                item.quantity shouldBe 2.0
                item.unit shouldBe "liters"
                item.isChecked.shouldBeFalse()
            }
        }

        test("can check item") {
            val itemId = UUID.randomUUID()

            transaction(db) {
                ListItem.new(itemId) {
                    list = ShoppingList[listId]
                    name = "Bread"
                    quantity = 1.0
                    unit = null
                    barcode = null
                    isChecked = false
                    checkedBy = null
                    createdBy = Account[accountId]
                    createdAt = Instant.now()
                    updatedAt = Instant.now()
                }
            }

            transaction(db) {
                val item = ListItem[itemId]
                item.isChecked = true
                item.checkedBy = Account[accountId]
                item.updatedAt = Instant.now()
            }

            transaction(db) {
                val item = ListItem.findById(itemId)
                item.shouldNotBeNull()
                item.isChecked.shouldBeTrue()
                item.checkedBy.shouldNotBeNull()
            }
        }
    })
