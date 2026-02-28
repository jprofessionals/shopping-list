package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class EmailClaimTest : FunSpec({
    lateinit var db: org.jetbrains.exposed.sql.Database
    lateinit var externalListService: ExternalListService

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts, Households, ShoppingLists, ListItems, ListShares,
            )
        }
        externalListService = ExternalListService(db)
    }

    afterTest {
        transaction(db) {
            ListItems.deleteAll()
            ListShares.deleteAll()
            ShoppingLists.deleteAll()
            Accounts.deleteAll()
        }
    }

    afterSpec {
        TestCleanup.dropAllTables(db)
    }

    test("claimPendingLists attaches lists to account and clears pending_email") {
        val result = externalListService.createExternalList(
            "Party supplies", "bob@example.com", emptyList(),
        )

        val account = transaction(db) {
            Account.new {
                this.email = "bob@example.com"
                this.displayName = "Bob"
                this.passwordHash = "hash"
                this.createdAt = java.time.Instant.now()
            }
        }

        val claimed = externalListService.claimPendingLists(account.id.value, "bob@example.com")

        claimed shouldBe 1
        transaction(db) {
            val list = ShoppingList.findById(result.listId)!!
            list.pendingEmail shouldBe null
            list.owner?.id?.value shouldBe account.id.value
        }
    }

    test("claimPendingLists returns 0 when no pending lists") {
        val account = transaction(db) {
            Account.new {
                this.email = "nobody@example.com"
                this.displayName = "Nobody"
                this.passwordHash = "hash"
                this.createdAt = java.time.Instant.now()
            }
        }

        val claimed = externalListService.claimPendingLists(account.id.value, "nobody@example.com")
        claimed shouldBe 0
    }
})
