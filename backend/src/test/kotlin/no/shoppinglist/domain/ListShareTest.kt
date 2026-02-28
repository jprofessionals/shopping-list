package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.RecurringItems
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListShareTest :
    FunSpec({

        lateinit var db: Database
        lateinit var ownerAccountId: UUID
        lateinit var sharedAccountId: UUID
        lateinit var listId: UUID

        beforeSpec {
            db = TestDatabaseConfig.init()
            ownerAccountId = UUID.randomUUID()
            sharedAccountId = UUID.randomUUID()
            listId = UUID.randomUUID()

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

                Account.new(ownerAccountId) {
                    email = "owner@example.com"
                    displayName = "Owner"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                Account.new(sharedAccountId) {
                    email = "shared@example.com"
                    displayName = "Shared User"
                    avatarUrl = null
                    createdAt = Instant.now()
                }

                ShoppingList.new(listId) {
                    name = "Test List"
                    owner = Account[ownerAccountId]
                    household = null
                    isPersonal = true
                    createdAt = Instant.now()
                }
            }
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        test("can create user share with write permission") {
            val shareId = UUID.randomUUID()

            transaction(db) {
                ListShare.new(shareId) {
                    list = ShoppingList[listId]
                    type = ShareType.USER
                    account = Account[sharedAccountId]
                    linkToken = null
                    permission = SharePermission.WRITE
                    createdAt = Instant.now()
                }
            }

            transaction(db) {
                val share = ListShare.findById(shareId)
                share.shouldNotBeNull()
                share.type shouldBe ShareType.USER
                share.permission shouldBe SharePermission.WRITE
                share.account.shouldNotBeNull()
                share.linkToken.shouldBeNull()
            }
        }

        test("can create link share with read permission") {
            val shareId = UUID.randomUUID()
            val token = UUID.randomUUID().toString()

            transaction(db) {
                ListShare.new(shareId) {
                    list = ShoppingList[listId]
                    type = ShareType.LINK
                    account = null
                    linkToken = token
                    permission = SharePermission.READ
                    createdAt = Instant.now()
                }
            }

            transaction(db) {
                val share = ListShare.findById(shareId)
                share.shouldNotBeNull()
                share.type shouldBe ShareType.LINK
                share.permission shouldBe SharePermission.READ
                share.account.shouldBeNull()
                share.linkToken shouldBe token
            }
        }

        test("can create link share with check permission") {
            val shareId = UUID.randomUUID()
            val token = UUID.randomUUID().toString()

            transaction(db) {
                ListShare.new(shareId) {
                    list = ShoppingList[listId]
                    type = ShareType.LINK
                    account = null
                    linkToken = token
                    permission = SharePermission.CHECK
                    createdAt = Instant.now()
                }
            }

            transaction(db) {
                val share = ListShare.findById(shareId)
                share.shouldNotBeNull()
                share.permission shouldBe SharePermission.CHECK
            }
        }
    })
