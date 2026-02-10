package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountTest :
    FunSpec({

        lateinit var db: Database

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts)
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(Accounts)
            }
        }

        test("can create and retrieve account") {
            val accountId = UUID.randomUUID()
            val email = "test@example.com"
            val displayName = "Test User"

            transaction(db) {
                Account.new(accountId) {
                    this.email = email
                    this.displayName = displayName
                    this.avatarUrl = null
                    this.createdAt = Instant.now()
                }
            }

            val retrieved =
                transaction(db) {
                    Account.findById(accountId)
                }

            retrieved.shouldNotBeNull()
            retrieved.email shouldBe email
            retrieved.displayName shouldBe displayName
        }
    })
