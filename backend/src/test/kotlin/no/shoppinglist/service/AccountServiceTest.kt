package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountServiceTest :
    FunSpec({
        lateinit var db: Database
        lateinit var accountService: AccountService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts)
            }
            accountService = AccountService(db)
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(Accounts)
            }
        }

        test("findByEmail returns null for non-existent email") {
            val result = accountService.findByEmail("nonexistent@example.com")
            result shouldBe null
        }

        test("findByEmail returns account for existing email") {
            val accountId = UUID.randomUUID()
            transaction(db) {
                Account.new(accountId) {
                    email = "existing@example.com"
                    displayName = "Existing User"
                    createdAt = Instant.now()
                }
            }

            val result = accountService.findByEmail("existing@example.com")
            result shouldNotBe null
            result!!.id.value shouldBe accountId
        }

        test("findByGoogleId returns account for existing Google ID") {
            val accountId = UUID.randomUUID()
            val googleId = "google-find-test"
            transaction(db) {
                Account.new(accountId) {
                    email = "googlefind@example.com"
                    displayName = "Google Find User"
                    this.googleId = googleId
                    createdAt = Instant.now()
                }
            }

            val result = accountService.findByGoogleId(googleId)
            result shouldNotBe null
            result!!.id.value shouldBe accountId
        }

        test("createFromGoogle creates new account") {
            val googleId = "new-google-id"
            val email = "newgoogle@example.com"
            val name = "New Google User"
            val avatar = "https://example.com/avatar.jpg"

            val account = accountService.createFromGoogle(googleId, email, name, avatar)

            account shouldNotBe null
            transaction(db) {
                account.email shouldBe email
                account.displayName shouldBe name
                account.googleId shouldBe googleId
                account.avatarUrl shouldBe avatar
            }
        }

        test("createLocal creates account with hashed password") {
            val email = "local@example.com"
            val name = "Local User"
            val password = "securePassword123"

            val account = accountService.createLocal(email, name, password)

            account shouldNotBe null
            transaction(db) {
                account.email shouldBe email
                account.displayName shouldBe name
                account.passwordHash shouldNotBe null
                account.passwordHash shouldNotBe password
            }
        }

        test("verifyPassword returns true for correct password") {
            val email = "verify@example.com"
            val password = "correctPassword"
            accountService.createLocal(email, "Verify User", password)

            val account = accountService.findByEmail(email)
            val result = accountService.verifyPassword(account!!, password)

            result shouldBe true
        }

        test("verifyPassword returns false for incorrect password") {
            val email = "verifywrong@example.com"
            val password = "correctPassword"
            accountService.createLocal(email, "Verify Wrong User", password)

            val account = accountService.findByEmail(email)
            val result = accountService.verifyPassword(account!!, "wrongPassword")

            result shouldBe false
        }
    })
