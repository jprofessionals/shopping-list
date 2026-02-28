package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.MembershipRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class HouseholdServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var householdService: HouseholdService
        lateinit var testAccountId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships, Comments)
            }
            householdService = HouseholdService(db)
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
            TestCleanup.dropAllTables(db)
        }

        test("create creates household with owner membership") {
            val household = householdService.create("My Home", testAccountId)

            household shouldNotBe null
            transaction(db) {
                household.name shouldBe "My Home"
            }

            val memberships = householdService.getMembers(household.id.value)
            memberships shouldHaveSize 1
            memberships[0].role shouldBe MembershipRole.OWNER
        }

        test("findById returns household") {
            val created = householdService.create("Test House", testAccountId)

            val found = householdService.findById(created.id.value)

            found shouldNotBe null
            transaction(db) {
                found!!.name shouldBe "Test House"
            }
        }

        test("findByAccountId returns all households for account") {
            householdService.create("House 1", testAccountId)
            householdService.create("House 2", testAccountId)

            val households = householdService.findByAccountId(testAccountId)

            households shouldHaveSize 2
        }

        test("update changes household name") {
            val household = householdService.create("Old Name", testAccountId)

            val updated = householdService.update(household.id.value, "New Name")

            updated shouldNotBe null
            transaction(db) {
                updated!!.name shouldBe "New Name"
            }
        }

        test("delete removes household and memberships") {
            val household = householdService.create("To Delete", testAccountId)
            val householdId = household.id.value

            val result = householdService.delete(householdId)

            result shouldBe true
            householdService.findById(householdId) shouldBe null
        }

        test("isOwner returns true for owner") {
            val household = householdService.create("Test", testAccountId)

            val result = householdService.isOwner(household.id.value, testAccountId)

            result shouldBe true
        }

        test("isOwner returns false for non-member") {
            val household = householdService.create("Test", testAccountId)
            val otherAccountId = UUID.randomUUID()

            val result = householdService.isOwner(household.id.value, otherAccountId)

            result shouldBe false
        }

        test("isMember returns true for member") {
            val household = householdService.create("Test", testAccountId)

            val result = householdService.isMember(household.id.value, testAccountId)

            result shouldBe true
        }
    })
