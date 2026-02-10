package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var service: ShoppingListService
        lateinit var testAccountId: UUID

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
                    Comments,
                )
            }
            service = ShoppingListService(db)
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
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(
                    Comments,
                    ListShares,
                    ListItems,
                    ShoppingLists,
                    HouseholdMemberships,
                    Households,
                    Accounts,
                )
            }
        }

        test("create creates a standalone shopping list") {
            val list = service.create("Groceries", testAccountId, null, false)

            list shouldNotBe null
            transaction(db) {
                list.name shouldBe "Groceries"
                list.owner.id.value shouldBe testAccountId
                list.household shouldBe null
                list.isPersonal shouldBe false
            }
        }

        test("findById returns list") {
            val created = service.create("Test List", testAccountId, null, false)

            val found = service.findById(created.id.value)

            found shouldNotBe null
            transaction(db) {
                found!!.name shouldBe "Test List"
            }
        }

        test("findById returns null for non-existent list") {
            val found = service.findById(UUID.randomUUID())
            found shouldBe null
        }

        test("getPermission returns WRITE for owner") {
            val list = service.create("Test", testAccountId, null, false)

            val permission = service.getPermission(list.id.value, testAccountId, null)

            permission shouldBe SharePermission.WRITE
        }

        test("getPermission returns WRITE for household member on non-personal list") {
            val householdService = HouseholdService(db)
            val household = householdService.create("Home", testAccountId)

            val otherAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(otherAccountId) {
                    email = "other-${UUID.randomUUID()}@example.com"
                    displayName = "Other User"
                    createdAt = Instant.now()
                }
            }
            householdService.addMember(household.id.value, otherAccountId, MembershipRole.MEMBER)

            val list = service.create("Shared List", testAccountId, household.id.value, false)

            val permission = service.getPermission(list.id.value, otherAccountId, null)

            permission shouldBe SharePermission.WRITE
        }

        test("getPermission returns null for household member on personal list") {
            val householdService = HouseholdService(db)
            val household = householdService.create("Home", testAccountId)

            val otherAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(otherAccountId) {
                    email = "other-${UUID.randomUUID()}@example.com"
                    displayName = "Other User"
                    createdAt = Instant.now()
                }
            }
            householdService.addMember(household.id.value, otherAccountId, MembershipRole.MEMBER)

            val list = service.create("Personal List", testAccountId, household.id.value, true)

            val permission = service.getPermission(list.id.value, otherAccountId, null)

            permission shouldBe null
        }

        test("getPermission returns null for non-member") {
            val list = service.create("Private", testAccountId, null, false)
            val otherAccountId = UUID.randomUUID()

            val permission = service.getPermission(list.id.value, otherAccountId, null)

            permission shouldBe null
        }

        test("update changes list name and isPersonal") {
            val list = service.create("Old Name", testAccountId, null, false)

            val updated = service.update(list.id.value, "New Name", true)

            updated shouldNotBe null
            transaction(db) {
                updated!!.name shouldBe "New Name"
                updated.isPersonal shouldBe true
            }
        }

        test("delete removes list") {
            val list = service.create("To Delete", testAccountId, null, false)
            val listId = list.id.value

            val result = service.delete(listId)

            result shouldBe true
            service.findById(listId) shouldBe null
        }

        test("findAccessibleByAccount returns owned lists") {
            service.create("My List", testAccountId, null, false)

            val lists = service.findAccessibleByAccount(testAccountId)

            lists.size shouldBe 1
            transaction(db) {
                lists[0].name shouldBe "My List"
            }
        }
    })
