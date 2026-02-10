package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListItemServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var service: ListItemService
        lateinit var listService: ShoppingListService
        lateinit var testAccountId: UUID
        lateinit var testListId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
            }
            service = ListItemService(db)
            listService = ShoppingListService(db)
        }

        beforeTest {
            testAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(testAccountId) {
                    email = "test-${UUID.randomUUID()}@example.com"
                    displayName = "Test User"
                    createdAt = Instant.now()
                }
            }
            val list = listService.create("Test List", testAccountId, null, false)
            testListId = list.id.value
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
            }
        }

        test("create adds item to list") {
            val item = service.create(testListId, "Milk", 2.0, "liters", null, testAccountId)

            item shouldNotBe null
            transaction(db) {
                item.name shouldBe "Milk"
                item.quantity shouldBe 2.0
                item.unit shouldBe "liters"
                item.isChecked shouldBe false
            }
        }

        test("findByListId returns all items") {
            service.create(testListId, "Milk", 1.0, null, null, testAccountId)
            service.create(testListId, "Bread", 2.0, null, null, testAccountId)

            val items = service.findByListId(testListId)

            items shouldHaveSize 2
        }

        test("update changes item properties") {
            val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

            val updated = service.update(item.id.value, "Whole Milk", 2.0, "liters")

            updated shouldNotBe null
            transaction(db) {
                updated!!.name shouldBe "Whole Milk"
                updated.quantity shouldBe 2.0
                updated.unit shouldBe "liters"
            }
        }

        test("toggleCheck changes isChecked and sets checkedBy") {
            val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

            val toggled = service.toggleCheck(item.id.value, testAccountId)

            toggled shouldNotBe null
            transaction(db) {
                toggled!!.isChecked shouldBe true
                toggled.checkedBy?.id?.value shouldBe testAccountId
            }
        }

        test("toggleCheck unchecks and clears checkedBy") {
            val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)
            service.toggleCheck(item.id.value, testAccountId)

            val toggled = service.toggleCheck(item.id.value, testAccountId)

            toggled shouldNotBe null
            transaction(db) {
                toggled!!.isChecked shouldBe false
                toggled.checkedBy shouldBe null
            }
        }

        test("delete removes item") {
            val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

            val result = service.delete(item.id.value)

            result shouldBe true
            service.findByListId(testListId) shouldHaveSize 0
        }
    })
