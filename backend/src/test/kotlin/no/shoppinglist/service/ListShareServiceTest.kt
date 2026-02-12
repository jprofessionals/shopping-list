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
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShareType
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListShareServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var service: ListShareService
        lateinit var listService: ShoppingListService
        lateinit var testAccountId: UUID
        lateinit var otherAccountId: UUID
        lateinit var testListId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
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
            }
            service = ListShareService(db)
            listService = ShoppingListService(db)
        }

        beforeTest {
            testAccountId = UUID.randomUUID()
            otherAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(testAccountId) {
                    email = "test-${UUID.randomUUID()}@example.com"
                    displayName = "Test User"
                    createdAt = Instant.now()
                }
                Account.new(otherAccountId) {
                    email = "other-${UUID.randomUUID()}@example.com"
                    displayName = "Other User"
                    createdAt = Instant.now()
                }
            }
            val list = listService.create("Test List", testAccountId, null, false)
            testListId = list.id.value
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(
                    ListShares,
                    ListItems,
                    RecurringItems,
                    ShoppingLists,
                    HouseholdMemberships,
                    Households,
                    Accounts,
                )
            }
        }

        test("createUserShare creates share with account") {
            val share = service.createUserShare(testListId, otherAccountId, SharePermission.READ)

            share shouldNotBe null
            transaction(db) {
                share.type shouldBe ShareType.USER
                share.account?.id?.value shouldBe otherAccountId
                share.permission shouldBe SharePermission.READ
                share.expiresAt shouldBe null
            }
        }

        test("createLinkShare creates share with token and expiration") {
            val share = service.createLinkShare(testListId, SharePermission.CHECK, 168)

            share shouldNotBe null
            transaction(db) {
                share.type shouldBe ShareType.LINK
                share.linkToken shouldNotBe null
                share.linkToken!!.length shouldBe 32
                share.permission shouldBe SharePermission.CHECK
                share.expiresAt shouldNotBe null
            }
        }

        test("findByListId returns all shares") {
            service.createUserShare(testListId, otherAccountId, SharePermission.READ)
            service.createLinkShare(testListId, SharePermission.WRITE, 168)

            val shares = service.findByListId(testListId)

            shares shouldHaveSize 2
        }

        test("delete removes share") {
            val share = service.createUserShare(testListId, otherAccountId, SharePermission.READ)

            val result = service.delete(share.id.value)

            result shouldBe true
            service.findByListId(testListId) shouldHaveSize 0
        }

        test("findByToken returns valid link share") {
            val share = service.createLinkShare(testListId, SharePermission.READ, 168)
            val token = transaction(db) { share.linkToken!! }

            val found = service.findByToken(token)

            found shouldNotBe null
        }

        test("findByToken returns null for expired link") {
            val share = service.createLinkShare(testListId, SharePermission.READ, -1)
            val token = transaction(db) { share.linkToken!! }

            val found = service.findByToken(token)

            found shouldBe null
        }
    })
