package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class HouseholdTest :
    FunSpec({

        lateinit var db: Database

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships)
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(HouseholdMemberships, Households, Accounts)
            }
        }

        test("can create household with owner") {
            val accountId = UUID.randomUUID()
            val householdId = UUID.randomUUID()

            transaction(db) {
                Account.new(accountId) {
                    email = "owner@example.com"
                    displayName = "Owner"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                Household.new(householdId) {
                    name = "Test Household"
                    createdAt = Instant.now()
                }

                HouseholdMembership.new {
                    account = Account[accountId]
                    household = Household[householdId]
                    role = MembershipRole.OWNER
                    joinedAt = Instant.now()
                }
            }

            val household = transaction(db) { Household.findById(householdId) }
            household.shouldNotBeNull()
            household.name shouldBe "Test Household"

            val memberships =
                transaction(db) {
                    HouseholdMembership.find { HouseholdMemberships.household eq householdId }.toList()
                }
            memberships shouldHaveSize 1
            memberships[0].role shouldBe MembershipRole.OWNER
        }
    })
