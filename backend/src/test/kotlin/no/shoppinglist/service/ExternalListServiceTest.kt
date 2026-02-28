package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveMinLength
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExternalListServiceTest :
    FunSpec({
        lateinit var db: org.jetbrains.exposed.sql.Database
        lateinit var externalListService: ExternalListService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(
                    Accounts,
                    Households,
                    ShoppingLists,
                    ListItems,
                    ListShares,
                )
            }
            externalListService = ExternalListService(db)
        }

        afterTest {
            transaction(db) {
                ListItems.deleteAll()
                ListShares.deleteAll()
                ShoppingLists.deleteAll()
            }
        }

        test("createExternalList creates list with share token") {
            val result = externalListService.createExternalList("Party supplies", null, emptyList())

            result.shareToken shouldHaveMinLength 32
            result.listId shouldNotBe null
        }

        test("createExternalList with email stores pending_email") {
            val result =
                externalListService.createExternalList(
                    "BBQ stuff",
                    "bob@example.com",
                    emptyList(),
                )

            transaction(db) {
                val list = ShoppingList.findById(result.listId)!!
                list.pendingEmail shouldBe "bob@example.com"
            }
        }

        test("createExternalList with items pre-populates the list") {
            val items =
                listOf(
                    ExternalItemRequest("Chips", 3.0, "bags"),
                    ExternalItemRequest("Salsa", 1.0, null),
                )
            val result = externalListService.createExternalList("Snacks", null, items)

            transaction(db) {
                val listItems = ListItem.find { ListItems.list eq result.listId }.toList()
                listItems.size shouldBe 2
                listItems.first().name shouldBe "Chips"
                listItems.first().quantity shouldBe 3.0
            }
        }
    })
