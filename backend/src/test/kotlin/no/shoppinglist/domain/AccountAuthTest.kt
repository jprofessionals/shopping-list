package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountAuthTest :
    FunSpec({
        lateinit var db: Database

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts)
            }
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        test("can create account with Google ID") {
            val accountId = UUID.randomUUID()
            val googleId = "google-123456"

            transaction(db) {
                Account.new(accountId) {
                    email = "google@example.com"
                    displayName = "Google User"
                    this.googleId = googleId
                    createdAt = Instant.now()
                }
            }

            val retrieved =
                transaction(db) {
                    Account.findById(accountId)
                }

            retrieved shouldNotBe null
            transaction(db) {
                retrieved!!.googleId shouldBe googleId
                retrieved.passwordHash shouldBe null
            }
        }

        test("can create account with password hash") {
            val accountId = UUID.randomUUID()
            val hash = "bcrypt-hash-placeholder"

            transaction(db) {
                Account.new(accountId) {
                    email = "local@example.com"
                    displayName = "Local User"
                    passwordHash = hash
                    createdAt = Instant.now()
                }
            }

            val retrieved =
                transaction(db) {
                    Account.findById(accountId)
                }

            retrieved shouldNotBe null
            transaction(db) {
                retrieved!!.passwordHash shouldBe hash
                retrieved.googleId shouldBe null
            }
        }
    })
