# Phase 2: Authentication Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement dual authentication system with Google OAuth and optional local username/password login, with JWT session tokens.

**Architecture:** Backend handles OAuth flow and password hashing. JWT tokens issued for session management. Local auth toggleable via config. Frontend provides login UI with conditional auth method display.

**Tech Stack:**
- Backend: Ktor Auth (OAuth2, JWT, Form), BCrypt for password hashing, Exposed ORM
- Web: Redux Toolkit for auth state, React components for login UI

---

## Phase Overview

| Task | Description |
|------|-------------|
| 1 | Add auth configuration to application.conf |
| 2 | Extend Account entity with auth fields |
| 3 | Create AuthConfig for loading settings |
| 4 | Create AccountService for user operations |
| 5 | Implement JWT token generation/validation |
| 6 | Implement Google OAuth routes |
| 7 | Implement local auth routes |
| 8 | Add /auth/me endpoint |
| 9 | Create auth Redux slice |
| 10 | Create Login component |
| 11 | Handle OAuth callback in frontend |
| 12 | Add authenticated state to App |
| 13 | Final verification |

---

## Task 1: Add Auth Configuration

**Files:**
- Modify: `backend/src/main/resources/application.conf`

**Step 1: Add auth configuration block**

Add to `application.conf`:

```hocon
auth {
    jwt {
        secret = "development-secret-change-in-production"
        secret = ${?JWT_SECRET}
        issuer = "shopping-list"
        audience = "shopping-list-users"
        realm = "Shopping List"
        expirationHours = 24
        expirationHours = ${?JWT_EXPIRATION_HOURS}
    }

    google {
        enabled = true
        enabled = ${?GOOGLE_AUTH_ENABLED}
        clientId = ""
        clientId = ${?GOOGLE_CLIENT_ID}
        clientSecret = ""
        clientSecret = ${?GOOGLE_CLIENT_SECRET}
        callbackUrl = "http://localhost:8080/auth/google/callback"
        callbackUrl = ${?GOOGLE_CALLBACK_URL}
    }

    local {
        enabled = true
        enabled = ${?LOCAL_AUTH_ENABLED}
    }
}
```

**Step 2: Verify configuration loads**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

---

## Task 2: Extend Account Entity

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/domain/Account.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/AccountAuthTest.kt`

**Step 1: Write failing test for new fields**

Create `backend/src/test/kotlin/no/shoppinglist/domain/AccountAuthTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountAuthTest : FunSpec({
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

    test("can create account with Google ID") {
        val accountId = UUID.randomUUID()
        val googleId = "google-123456"

        transaction(db) {
            Account.new(accountId) {
                email = "google@example.com"
                displayName = "Google User"
                googleId = googleId
                createdAt = Instant.now()
            }
        }

        val retrieved = transaction(db) {
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

        val retrieved = transaction(db) {
            Account.findById(accountId)
        }

        retrieved shouldNotBe null
        transaction(db) {
            retrieved!!.passwordHash shouldBe hash
            retrieved.googleId shouldBe null
        }
    }
})
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "no.shoppinglist.domain.AccountAuthTest"`
Expected: FAIL - googleId and passwordHash not defined

**Step 3: Add auth fields to Account entity**

Modify `backend/src/main/kotlin/no/shoppinglist/domain/Account.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

object Accounts : UUIDTable("accounts") {
    val email = varchar("email", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val googleId = varchar("google_id", 255).uniqueIndex().nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val createdAt = timestamp("created_at")
}

class Account(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(Accounts)

    var email by Accounts.email
    var displayName by Accounts.displayName
    var avatarUrl by Accounts.avatarUrl
    var googleId by Accounts.googleId
    var passwordHash by Accounts.passwordHash
    var createdAt by Accounts.createdAt
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.domain.AccountAuthTest"`
Expected: PASS

**Step 5: Run all tests to ensure no regressions**

Run: `./gradlew test`
Expected: All tests pass

**Step 6: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/Account.kt backend/src/test/kotlin/no/shoppinglist/domain/AccountAuthTest.kt
git commit -m "feat: add googleId and passwordHash fields to Account entity"
```

---

## Task 3: Create AuthConfig

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/config/AuthConfig.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/config/AuthConfigTest.kt`

**Step 1: Write failing test**

Create `backend/src/test/kotlin/no/shoppinglist/config/AuthConfigTest.kt`:

```kotlin
package no.shoppinglist.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig

class AuthConfigTest : FunSpec({
    test("loads JWT configuration") {
        val config = MapApplicationConfig().apply {
            put("auth.jwt.secret", "test-secret")
            put("auth.jwt.issuer", "test-issuer")
            put("auth.jwt.audience", "test-audience")
            put("auth.jwt.realm", "test-realm")
            put("auth.jwt.expirationHours", "48")
        }

        val authConfig = AuthConfig.fromApplicationConfig(config)

        authConfig.jwt.secret shouldBe "test-secret"
        authConfig.jwt.issuer shouldBe "test-issuer"
        authConfig.jwt.audience shouldBe "test-audience"
        authConfig.jwt.realm shouldBe "test-realm"
        authConfig.jwt.expirationHours shouldBe 48
    }

    test("loads Google OAuth configuration") {
        val config = MapApplicationConfig().apply {
            put("auth.google.enabled", "true")
            put("auth.google.clientId", "google-client-id")
            put("auth.google.clientSecret", "google-secret")
            put("auth.google.callbackUrl", "http://localhost/callback")
        }

        val authConfig = AuthConfig.fromApplicationConfig(config)

        authConfig.google.enabled shouldBe true
        authConfig.google.clientId shouldBe "google-client-id"
        authConfig.google.clientSecret shouldBe "google-secret"
        authConfig.google.callbackUrl shouldBe "http://localhost/callback"
    }

    test("loads local auth configuration") {
        val config = MapApplicationConfig().apply {
            put("auth.local.enabled", "false")
        }

        val authConfig = AuthConfig.fromApplicationConfig(config)

        authConfig.local.enabled shouldBe false
    }
})
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "no.shoppinglist.config.AuthConfigTest"`
Expected: FAIL - AuthConfig not defined

**Step 3: Create AuthConfig**

Create `backend/src/main/kotlin/no/shoppinglist/config/AuthConfig.kt`:

```kotlin
package no.shoppinglist.config

import io.ktor.server.config.ApplicationConfig

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationHours: Int
)

data class GoogleAuthConfig(
    val enabled: Boolean,
    val clientId: String,
    val clientSecret: String,
    val callbackUrl: String
)

data class LocalAuthConfig(
    val enabled: Boolean
)

data class AuthConfig(
    val jwt: JwtConfig,
    val google: GoogleAuthConfig,
    val local: LocalAuthConfig
) {
    companion object {
        fun fromApplicationConfig(config: ApplicationConfig): AuthConfig {
            return AuthConfig(
                jwt = JwtConfig(
                    secret = config.property("auth.jwt.secret").getString(),
                    issuer = config.property("auth.jwt.issuer").getString(),
                    audience = config.property("auth.jwt.audience").getString(),
                    realm = config.property("auth.jwt.realm").getString(),
                    expirationHours = config.property("auth.jwt.expirationHours").getString().toInt()
                ),
                google = GoogleAuthConfig(
                    enabled = config.propertyOrNull("auth.google.enabled")?.getString()?.toBoolean() ?: false,
                    clientId = config.propertyOrNull("auth.google.clientId")?.getString() ?: "",
                    clientSecret = config.propertyOrNull("auth.google.clientSecret")?.getString() ?: "",
                    callbackUrl = config.propertyOrNull("auth.google.callbackUrl")?.getString() ?: ""
                ),
                local = LocalAuthConfig(
                    enabled = config.propertyOrNull("auth.local.enabled")?.getString()?.toBoolean() ?: true
                )
            )
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.config.AuthConfigTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/config/AuthConfig.kt backend/src/test/kotlin/no/shoppinglist/config/AuthConfigTest.kt
git commit -m "feat: add AuthConfig for JWT, Google OAuth, and local auth settings"
```

---

## Task 4: Create AccountService

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/AccountService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/AccountServiceTest.kt`

**Step 1: Write failing tests**

Create `backend/src/test/kotlin/no/shoppinglist/service/AccountServiceTest.kt`:

```kotlin
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

class AccountServiceTest : FunSpec({
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
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "no.shoppinglist.service.AccountServiceTest"`
Expected: FAIL - AccountService not defined

**Step 3: Add BCrypt dependency**

Add to `backend/build.gradle.kts` dependencies:

```kotlin
implementation("at.favre.lib:bcrypt:0.10.2")
```

**Step 4: Create AccountService**

Create `backend/src/main/kotlin/no/shoppinglist/service/AccountService.kt`:

```kotlin
package no.shoppinglist.service

import at.favre.lib.crypto.bcrypt.BCrypt
import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Accounts
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class AccountService(private val db: Database) {

    fun findByEmail(email: String): Account? {
        return transaction(db) {
            Account.find { Accounts.email eq email }.firstOrNull()
        }
    }

    fun findByGoogleId(googleId: String): Account? {
        return transaction(db) {
            Account.find { Accounts.googleId eq googleId }.firstOrNull()
        }
    }

    fun findById(id: UUID): Account? {
        return transaction(db) {
            Account.findById(id)
        }
    }

    fun createFromGoogle(
        googleId: String,
        email: String,
        displayName: String,
        avatarUrl: String?
    ): Account {
        return transaction(db) {
            Account.new {
                this.email = email
                this.displayName = displayName
                this.googleId = googleId
                this.avatarUrl = avatarUrl
                this.createdAt = Instant.now()
            }
        }
    }

    fun createLocal(email: String, displayName: String, password: String): Account {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(db) {
            Account.new {
                this.email = email
                this.displayName = displayName
                this.passwordHash = hash
                this.createdAt = Instant.now()
            }
        }
    }

    fun verifyPassword(account: Account, password: String): Boolean {
        val hash = transaction(db) { account.passwordHash } ?: return false
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    fun linkGoogleAccount(account: Account, googleId: String, avatarUrl: String?) {
        transaction(db) {
            account.googleId = googleId
            if (avatarUrl != null && account.avatarUrl == null) {
                account.avatarUrl = avatarUrl
            }
        }
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.service.AccountServiceTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/kotlin/no/shoppinglist/service/AccountService.kt backend/src/test/kotlin/no/shoppinglist/service/AccountServiceTest.kt
git commit -m "feat: add AccountService with Google and local auth support"
```

---

## Task 5: Implement JWT Token Service

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/JwtService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/JwtServiceTest.kt`

**Step 1: Write failing tests**

Create `backend/src/test/kotlin/no/shoppinglist/service/JwtServiceTest.kt`:

```kotlin
package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.JwtConfig
import java.util.UUID

class JwtServiceTest : FunSpec({
    val jwtConfig = JwtConfig(
        secret = "test-secret-key-at-least-32-chars-long",
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        expirationHours = 24
    )
    val jwtService = JwtService(jwtConfig)

    test("generates valid token for account") {
        val accountId = UUID.randomUUID()
        val email = "test@example.com"

        val token = jwtService.generateToken(accountId, email)

        token shouldNotBe null
        token.isNotEmpty() shouldBe true
    }

    test("validates and extracts account ID from token") {
        val accountId = UUID.randomUUID()
        val email = "test@example.com"

        val token = jwtService.generateToken(accountId, email)
        val extractedId = jwtService.validateAndGetAccountId(token)

        extractedId shouldBe accountId
    }

    test("returns null for invalid token") {
        val result = jwtService.validateAndGetAccountId("invalid-token")

        result shouldBe null
    }

    test("returns null for expired token") {
        val expiredConfig = jwtConfig.copy(expirationHours = -1)
        val expiredJwtService = JwtService(expiredConfig)
        val accountId = UUID.randomUUID()

        val token = expiredJwtService.generateToken(accountId, "test@example.com")
        val result = jwtService.validateAndGetAccountId(token)

        result shouldBe null
    }
})
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "no.shoppinglist.service.JwtServiceTest"`
Expected: FAIL - JwtService not defined

**Step 3: Create JwtService**

Create `backend/src/main/kotlin/no/shoppinglist/service/JwtService.kt`:

```kotlin
package no.shoppinglist.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import no.shoppinglist.config.JwtConfig
import java.util.Date
import java.util.UUID

class JwtService(private val config: JwtConfig) {

    private val algorithm = Algorithm.HMAC256(config.secret)

    private val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun generateToken(accountId: UUID, email: String): String {
        val now = System.currentTimeMillis()
        val expiration = now + (config.expirationHours * 60 * 60 * 1000L)

        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(accountId.toString())
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiration))
            .sign(algorithm)
    }

    fun validateAndGetAccountId(token: String): UUID? {
        return try {
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (e: JWTVerificationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun getAlgorithm(): Algorithm = algorithm
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.service.JwtServiceTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/JwtService.kt backend/src/test/kotlin/no/shoppinglist/service/JwtServiceTest.kt
git commit -m "feat: add JwtService for token generation and validation"
```

---

## Task 6: Implement Auth Module and Google OAuth Routes

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/AuthRoutes.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt`

**Step 1: Write failing test for auth configuration endpoint**

Create `backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt`:

```kotlin
package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthRoutesTest : FunSpec({
    test("GET /auth/config returns available auth methods") {
        testApplication {
            val response = client.get("/auth/config")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json.containsKey("googleEnabled") shouldBe true
            json.containsKey("localEnabled") shouldBe true
        }
    }

    test("GET /auth/me returns 401 when not authenticated") {
        testApplication {
            val response = client.get("/auth/me")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "no.shoppinglist.routes.AuthRoutesTest"`
Expected: FAIL - routes not defined

**Step 3: Create AuthRoutes**

Create `backend/src/main/kotlin/no/shoppinglist/routes/AuthRoutes.kt`:

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.JwtService
import java.util.UUID

@Serializable
data class AuthConfigResponse(
    val googleEnabled: Boolean,
    val localEnabled: Boolean,
    val googleClientId: String?
)

@Serializable
data class LocalLoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LocalRegisterRequest(
    val email: String,
    val displayName: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?
)

fun Route.authRoutes(
    authConfig: AuthConfig,
    accountService: AccountService,
    jwtService: JwtService
) {
    route("/auth") {
        get("/config") {
            call.respond(
                AuthConfigResponse(
                    googleEnabled = authConfig.google.enabled,
                    localEnabled = authConfig.local.enabled,
                    googleClientId = if (authConfig.google.enabled) authConfig.google.clientId else null
                )
            )
        }

        if (authConfig.local.enabled) {
            post("/login") {
                val request = call.receive<LocalLoginRequest>()
                val account = accountService.findByEmail(request.email)

                if (account == null || !accountService.verifyPassword(account, request.password)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                val token = jwtService.generateToken(account.id.value, account.email)
                call.respond(
                    LoginResponse(
                        token = token,
                        user = UserResponse(
                            id = account.id.value.toString(),
                            email = account.email,
                            displayName = account.displayName,
                            avatarUrl = account.avatarUrl
                        )
                    )
                )
            }

            post("/register") {
                val request = call.receive<LocalRegisterRequest>()

                if (accountService.findByEmail(request.email) != null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already registered"))
                    return@post
                }

                val account = accountService.createLocal(request.email, request.displayName, request.password)
                val token = jwtService.generateToken(account.id.value, account.email)

                call.respond(
                    HttpStatusCode.Created,
                    LoginResponse(
                        token = token,
                        user = UserResponse(
                            id = account.id.value.toString(),
                            email = account.email,
                            displayName = account.displayName,
                            avatarUrl = account.avatarUrl
                        )
                    )
                )
            }
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val accountId = principal?.subject?.let { UUID.fromString(it) }

                if (accountId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val account = accountService.findById(accountId)
                if (account == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Account not found"))
                    return@get
                }

                call.respond(
                    UserResponse(
                        id = account.id.value.toString(),
                        email = account.email,
                        displayName = account.displayName,
                        avatarUrl = account.avatarUrl
                    )
                )
            }

            post("/logout") {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
            }
        }
    }
}
```

**Step 4: Update Application.kt with auth configuration**

Replace `backend/src/main/kotlin/no/shoppinglist/Application.kt`:

```kotlin
package no.shoppinglist

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.config.DatabaseConfig
import no.shoppinglist.routes.authRoutes
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.JwtService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val authConfig = AuthConfig.fromApplicationConfig(environment.config)
    val jwtService = JwtService(authConfig.jwt)

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = authConfig.jwt.realm
            verifier(
                JWT.require(Algorithm.HMAC256(authConfig.jwt.secret))
                    .withIssuer(authConfig.jwt.issuer)
                    .withAudience(authConfig.jwt.audience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    DatabaseConfig.init(environment)
    val db = DatabaseConfig.getDatabase()
    val accountService = AccountService(db)

    routing {
        get("/health") {
            call.respondText("OK")
        }

        authRoutes(authConfig, accountService, jwtService)
    }
}
```

**Step 5: Update DatabaseConfig to expose database**

Modify `backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt`:

```kotlin
package no.shoppinglist.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.ApplicationEnvironment
import org.jetbrains.exposed.sql.Database

object DatabaseConfig {
    private var database: Database? = null

    fun init(environment: ApplicationEnvironment) {
        val config = HikariConfig().apply {
            jdbcUrl = environment.config.property("database.url").getString()
            username = environment.config.property("database.user").getString()
            password = environment.config.property("database.password").getString()
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        database = Database.connect(HikariDataSource(config))
    }

    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call init() first.")
    }
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.routes.AuthRoutesTest"`
Expected: PASS

**Step 7: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 8: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/AuthRoutes.kt backend/src/main/kotlin/no/shoppinglist/Application.kt backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt backend/src/main/resources/application.conf
git commit -m "feat: add auth routes with JWT authentication and local login"
```

---

## Task 7: Implement Google OAuth Routes

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/AuthRoutes.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Add Google OAuth client dependency**

Add to `backend/build.gradle.kts` dependencies:

```kotlin
implementation("io.ktor:ktor-client-core:$ktor_version")
implementation("io.ktor:ktor-client-cio:$ktor_version")
implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
```

**Step 2: Create GoogleAuthService**

Create `backend/src/main/kotlin/no/shoppinglist/service/GoogleAuthService.kt`:

```kotlin
package no.shoppinglist.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.shoppinglist.config.GoogleAuthConfig
import java.net.URLEncoder

@Serializable
data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String,
    val picture: String? = null
)

class GoogleAuthService(private val config: GoogleAuthConfig) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    fun getAuthorizationUrl(state: String): String {
        val params = mapOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.callbackUrl,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "consent"
        )
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?$queryString"
    }

    suspend fun exchangeCodeForTokens(code: String): GoogleTokenResponse {
        return client.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "code=$code" +
                    "&client_id=${config.clientId}" +
                    "&client_secret=${config.clientSecret}" +
                    "&redirect_uri=${URLEncoder.encode(config.callbackUrl, "UTF-8")}" +
                    "&grant_type=authorization_code"
            )
        }.body()
    }

    suspend fun getUserInfo(accessToken: String): GoogleUserInfo {
        return client.get("https://www.googleapis.com/oauth2/v2/userinfo") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()
    }
}
```

**Step 3: Add Google OAuth routes**

Add to `AuthRoutes.kt` inside the `route("/auth")` block:

```kotlin
if (authConfig.google.enabled) {
    val googleAuthService = GoogleAuthService(authConfig.google)

    get("/google") {
        val state = UUID.randomUUID().toString()
        val authUrl = googleAuthService.getAuthorizationUrl(state)
        call.respondRedirect(authUrl)
    }

    get("/google/callback") {
        val code = call.request.queryParameters["code"]
        val error = call.request.queryParameters["error"]

        if (error != null) {
            call.respondRedirect("http://localhost:5173/login?error=$error")
            return@get
        }

        if (code == null) {
            call.respondRedirect("http://localhost:5173/login?error=no_code")
            return@get
        }

        try {
            val tokens = googleAuthService.exchangeCodeForTokens(code)
            val userInfo = googleAuthService.getUserInfo(tokens.accessToken)

            var account = accountService.findByGoogleId(userInfo.id)
            if (account == null) {
                account = accountService.findByEmail(userInfo.email)
                if (account != null) {
                    accountService.linkGoogleAccount(account, userInfo.id, userInfo.picture)
                } else {
                    account = accountService.createFromGoogle(
                        googleId = userInfo.id,
                        email = userInfo.email,
                        displayName = userInfo.name,
                        avatarUrl = userInfo.picture
                    )
                }
            }

            val token = jwtService.generateToken(account.id.value, account.email)
            call.respondRedirect("http://localhost:5173/auth/callback?token=$token")
        } catch (e: Exception) {
            call.respondRedirect("http://localhost:5173/login?error=auth_failed")
        }
    }
}
```

**Step 4: Update imports in AuthRoutes.kt**

Add at top of file:

```kotlin
import no.shoppinglist.service.GoogleAuthService
```

**Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 6: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/GoogleAuthService.kt backend/src/main/kotlin/no/shoppinglist/routes/AuthRoutes.kt backend/build.gradle.kts
git commit -m "feat: add Google OAuth authentication flow"
```

---

## Task 8: Add More Auth Route Tests

**Files:**
- Modify: `backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt`

**Step 1: Add tests for local auth routes**

Add to `AuthRoutesTest.kt`:

```kotlin
test("POST /auth/register creates new account") {
    testApplication {
        application {
            // Test module setup with local auth enabled
        }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","displayName":"Test User","password":"password123"}""")
        }

        response.status shouldBe HttpStatusCode.Created
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json.containsKey("token") shouldBe true
        json.containsKey("user") shouldBe true
    }
}

test("POST /auth/login returns token for valid credentials") {
    testApplication {
        // Register first, then login
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"password123"}""")
        }

        response.status shouldBe HttpStatusCode.OK
    }
}

test("POST /auth/login returns 401 for invalid credentials") {
    testApplication {
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nonexistent@example.com","password":"wrong"}""")
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }
}
```

**Step 2: Run tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt
git commit -m "test: add auth route tests for login and register"
```

---

## Task 9: Create Auth Redux Slice

**Files:**
- Create: `web/src/store/authSlice.ts`
- Modify: `web/src/store/store.ts`
- Create: `web/src/store/authSlice.test.ts`

**Step 1: Write failing test**

Create `web/src/store/authSlice.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import authReducer, {
  loginStart,
  loginSuccess,
  loginFailure,
  logout,
  AuthState,
} from './authSlice';

describe('authSlice', () => {
  const initialState: AuthState = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
  };

  it('should handle initial state', () => {
    expect(authReducer(undefined, { type: 'unknown' })).toEqual(initialState);
  });

  it('should handle loginStart', () => {
    const state = authReducer(initialState, loginStart());
    expect(state.isLoading).toBe(true);
    expect(state.error).toBe(null);
  });

  it('should handle loginSuccess', () => {
    const user = { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null };
    const state = authReducer(initialState, loginSuccess({ user, token: 'test-token' }));
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).toEqual(user);
    expect(state.token).toBe('test-token');
    expect(state.isLoading).toBe(false);
  });

  it('should handle loginFailure', () => {
    const state = authReducer(initialState, loginFailure('Invalid credentials'));
    expect(state.isLoading).toBe(false);
    expect(state.error).toBe('Invalid credentials');
    expect(state.isAuthenticated).toBe(false);
  });

  it('should handle logout', () => {
    const loggedInState: AuthState = {
      user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
      token: 'token',
      isAuthenticated: true,
      isLoading: false,
      error: null,
    };
    const state = authReducer(loggedInState, logout());
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBe(null);
    expect(state.token).toBe(null);
  });
});
```

**Step 2: Run test to verify it fails**

Run: `npm run test:run`
Expected: FAIL - authSlice not defined

**Step 3: Create authSlice**

Create `web/src/store/authSlice.ts`:

```typescript
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface User {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

const initialState: AuthState = {
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    loginStart(state) {
      state.isLoading = true;
      state.error = null;
    },
    loginSuccess(state, action: PayloadAction<{ user: User; token: string }>) {
      state.isLoading = false;
      state.isAuthenticated = true;
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.error = null;
    },
    loginFailure(state, action: PayloadAction<string>) {
      state.isLoading = false;
      state.isAuthenticated = false;
      state.error = action.payload;
    },
    logout(state) {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      state.error = null;
    },
  },
});

export const { loginStart, loginSuccess, loginFailure, logout } = authSlice.actions;
export default authSlice.reducer;
```

**Step 4: Update store.ts**

```typescript
import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

**Step 5: Run test to verify it passes**

Run: `npm run test:run`
Expected: PASS

**Step 6: Commit**

```bash
git add web/src/store/authSlice.ts web/src/store/authSlice.test.ts web/src/store/store.ts
git commit -m "feat: add auth Redux slice with login/logout actions"
```

---

## Task 10: Create Login Component

**Files:**
- Create: `web/src/components/Login.tsx`
- Create: `web/src/components/Login.test.tsx`

**Step 1: Write failing test**

Create `web/src/components/Login.test.tsx`:

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import Login from './Login';
import authReducer from '../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { auth: authReducer },
  });

describe('Login', () => {
  it('renders login form when local auth is enabled', () => {
    render(
      <Provider store={createTestStore()}>
        <Login authConfig={{ googleEnabled: false, localEnabled: true, googleClientId: null }} />
      </Provider>
    );

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('renders Google login button when Google auth is enabled', () => {
    render(
      <Provider store={createTestStore()}>
        <Login
          authConfig={{ googleEnabled: true, localEnabled: false, googleClientId: 'test-client' }}
        />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument();
  });

  it('renders both options when both are enabled', () => {
    render(
      <Provider store={createTestStore()}>
        <Login
          authConfig={{ googleEnabled: true, localEnabled: true, googleClientId: 'test-client' }}
        />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `npm run test:run`
Expected: FAIL - Login component not defined

**Step 3: Create Login component**

Create `web/src/components/Login.tsx`:

```typescript
import { useState } from 'react';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { loginStart, loginSuccess, loginFailure } from '../store/authSlice';

interface AuthConfig {
  googleEnabled: boolean;
  localEnabled: boolean;
  googleClientId: string | null;
}

interface LoginProps {
  authConfig: AuthConfig;
}

export default function Login({ authConfig }: LoginProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isRegister, setIsRegister] = useState(false);
  const [displayName, setDisplayName] = useState('');

  const dispatch = useAppDispatch();
  const { isLoading, error } = useAppSelector((state) => state.auth);

  const handleLocalSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    dispatch(loginStart());

    try {
      const endpoint = isRegister ? '/auth/register' : '/auth/login';
      const body = isRegister
        ? { email, password, displayName }
        : { email, password };

      const response = await fetch(`http://localhost:8080${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        const data = await response.json();
        throw new Error(data.error || 'Authentication failed');
      }

      const data = await response.json();
      localStorage.setItem('token', data.token);
      dispatch(loginSuccess({ user: data.user, token: data.token }));
    } catch (err) {
      dispatch(loginFailure(err instanceof Error ? err.message : 'Authentication failed'));
    }
  };

  const handleGoogleLogin = () => {
    window.location.href = 'http://localhost:8080/auth/google';
  };

  return (
    <div className="flex min-h-full flex-col justify-center px-6 py-12 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-sm">
        <h2 className="mt-10 text-center text-2xl font-bold leading-9 tracking-tight text-gray-900">
          {isRegister ? 'Create your account' : 'Sign in to your account'}
        </h2>
      </div>

      <div className="mt-10 sm:mx-auto sm:w-full sm:max-w-sm">
        {error && (
          <div className="mb-4 rounded-md bg-red-50 p-4 text-sm text-red-700">{error}</div>
        )}

        {authConfig.googleEnabled && (
          <div className="mb-6">
            <button
              type="button"
              onClick={handleGoogleLogin}
              className="flex w-full items-center justify-center gap-3 rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
            >
              <svg className="h-5 w-5" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                />
              </svg>
              Continue with Google
            </button>
          </div>
        )}

        {authConfig.googleEnabled && authConfig.localEnabled && (
          <div className="relative mb-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-gray-300" />
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="bg-gray-100 px-2 text-gray-500">Or continue with email</span>
            </div>
          </div>
        )}

        {authConfig.localEnabled && (
          <form className="space-y-6" onSubmit={handleLocalSubmit}>
            <div>
              <label htmlFor="email" className="block text-sm font-medium leading-6 text-gray-900">
                Email address
              </label>
              <div className="mt-2">
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
                />
              </div>
            </div>

            {isRegister && (
              <div>
                <label
                  htmlFor="displayName"
                  className="block text-sm font-medium leading-6 text-gray-900"
                >
                  Display name
                </label>
                <div className="mt-2">
                  <input
                    id="displayName"
                    name="displayName"
                    type="text"
                    required
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
                  />
                </div>
              </div>
            )}

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium leading-6 text-gray-900"
              >
                Password
              </label>
              <div className="mt-2">
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                disabled={isLoading}
                className="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold leading-6 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
              >
                {isLoading ? 'Loading...' : isRegister ? 'Create account' : 'Sign in'}
              </button>
            </div>
          </form>
        )}

        {authConfig.localEnabled && (
          <p className="mt-10 text-center text-sm text-gray-500">
            {isRegister ? 'Already have an account?' : "Don't have an account?"}{' '}
            <button
              type="button"
              onClick={() => setIsRegister(!isRegister)}
              className="font-semibold leading-6 text-indigo-600 hover:text-indigo-500"
            >
              {isRegister ? 'Sign in' : 'Create one'}
            </button>
          </p>
        )}
      </div>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `npm run test:run`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/Login.tsx web/src/components/Login.test.tsx
git commit -m "feat: add Login component with Google and local auth support"
```

---

## Task 11: Handle OAuth Callback

**Files:**
- Create: `web/src/components/AuthCallback.tsx`
- Create: `web/src/components/AuthCallback.test.tsx`

**Step 1: Write failing test**

Create `web/src/components/AuthCallback.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AuthCallback from './AuthCallback';
import authReducer from '../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { auth: authReducer },
  });

describe('AuthCallback', () => {
  beforeEach(() => {
    vi.stubGlobal('location', {
      search: '?token=test-token',
      href: '',
    });
  });

  it('shows loading state while processing token', () => {
    render(
      <Provider store={createTestStore()}>
        <AuthCallback />
      </Provider>
    );

    expect(screen.getByText(/completing sign in/i)).toBeInTheDocument();
  });
});
```

**Step 2: Create AuthCallback component**

Create `web/src/components/AuthCallback.tsx`:

```typescript
import { useEffect } from 'react';
import { useAppDispatch } from '../store/hooks';
import { loginStart, loginSuccess, loginFailure } from '../store/authSlice';

export default function AuthCallback() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const error = params.get('error');

    if (error) {
      dispatch(loginFailure(error));
      window.location.href = '/login?error=' + error;
      return;
    }

    if (token) {
      dispatch(loginStart());
      localStorage.setItem('token', token);

      fetch('http://localhost:8080/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      })
        .then((res) => {
          if (!res.ok) throw new Error('Failed to fetch user');
          return res.json();
        })
        .then((user) => {
          dispatch(loginSuccess({ user, token }));
          window.location.href = '/';
        })
        .catch((err) => {
          dispatch(loginFailure(err.message));
          window.location.href = '/login?error=auth_failed';
        });
    }
  }, [dispatch]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <div className="mb-4 h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent mx-auto"></div>
        <p className="text-gray-600">Completing sign in...</p>
      </div>
    </div>
  );
}
```

**Step 3: Run test to verify it passes**

Run: `npm run test:run`
Expected: PASS

**Step 4: Commit**

```bash
git add web/src/components/AuthCallback.tsx web/src/components/AuthCallback.test.tsx
git commit -m "feat: add AuthCallback component for OAuth redirect handling"
```

---

## Task 12: Update App with Auth State

**Files:**
- Modify: `web/src/App.tsx`
- Modify: `web/src/App.test.tsx`

**Step 1: Update App component**

Replace `web/src/App.tsx`:

```typescript
import { useEffect, useState } from 'react';
import { useAppDispatch, useAppSelector } from './store/hooks';
import { loginSuccess, logout } from './store/authSlice';
import Login from './components/Login';
import AuthCallback from './components/AuthCallback';

interface AuthConfig {
  googleEnabled: boolean;
  localEnabled: boolean;
  googleClientId: string | null;
}

function App() {
  const [authConfig, setAuthConfig] = useState<AuthConfig | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const dispatch = useAppDispatch();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);

  useEffect(() => {
    const path = window.location.pathname;
    if (path === '/auth/callback') {
      setIsInitializing(false);
      return;
    }

    fetch('http://localhost:8080/auth/config')
      .then((res) => res.json())
      .then(setAuthConfig)
      .catch(console.error);

    const token = localStorage.getItem('token');
    if (token) {
      fetch('http://localhost:8080/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      })
        .then((res) => {
          if (!res.ok) throw new Error('Invalid token');
          return res.json();
        })
        .then((user) => {
          dispatch(loginSuccess({ user, token }));
        })
        .catch(() => {
          localStorage.removeItem('token');
        })
        .finally(() => setIsInitializing(false));
    } else {
      setIsInitializing(false);
    }
  }, [dispatch]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    dispatch(logout());
  };

  if (window.location.pathname === '/auth/callback') {
    return <AuthCallback />;
  }

  if (isInitializing) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
      </div>
    );
  }

  if (!isAuthenticated && authConfig) {
    return (
      <div className="min-h-screen bg-gray-100">
        <Login authConfig={authConfig} />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-white shadow">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-6 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold tracking-tight text-gray-900">Shopping List</h1>
          {user && (
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">{user.displayName}</span>
              <button
                onClick={handleLogout}
                className="rounded-md bg-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-300"
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </header>
      <main>
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <div className="rounded-lg bg-white p-6 shadow">
            <p className="text-gray-600">Welcome, {user?.displayName}! Your shopping lists will appear here.</p>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
```

**Step 2: Update App tests**

Replace `web/src/App.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from './App';
import authReducer from './store/authSlice';

const createTestStore = (preloadedState = {}) =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState,
  });

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ googleEnabled: true, localEnabled: true, googleClientId: 'test' }),
      })
    ) as unknown as typeof fetch);
    vi.stubGlobal('location', { pathname: '/' });
  });

  it('renders loading state initially', () => {
    render(
      <Provider store={createTestStore()}>
        <App />
      </Provider>
    );

    // App shows loading spinner initially
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('renders welcome message when authenticated', async () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    vi.stubGlobal('fetch', vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ id: '1', email: 'test@example.com', displayName: 'Test User' }),
      })
    ) as unknown as typeof fetch);

    render(
      <Provider store={store}>
        <App />
      </Provider>
    );

    expect(await screen.findByText(/welcome, test user/i)).toBeInTheDocument();
  });
});
```

**Step 3: Run tests**

Run: `npm run test:run`
Expected: PASS

**Step 4: Run lint**

Run: `npm run lint`
Expected: No errors

**Step 5: Commit**

```bash
git add web/src/App.tsx web/src/App.test.tsx
git commit -m "feat: integrate auth state with App component"
```

---

## Task 13: Final Verification

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test detekt ktlintCheck`
Expected: All pass

**Step 2: Run all frontend tests**

Run: `cd web && npm run test:run && npm run lint`
Expected: All pass

**Step 3: Build both projects**

Run:
```bash
cd backend && ./gradlew build
cd ../web && npm run build
```
Expected: Both build successfully

**Step 4: Manual verification with Playwright**

Start backend and frontend, take screenshot to verify:
1. Login page renders with both Google and local options
2. Can toggle between sign in and register
3. Form validation works

**Step 5: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final verification and fixes for Phase 2 auth"
```

---

## Summary

Phase 2 implements:

**Backend:**
- Auth configuration with toggleable Google OAuth and local auth
- Account entity with googleId and passwordHash fields
- AccountService for user management with BCrypt password hashing
- JwtService for token generation/validation
- Auth routes: /auth/config, /auth/login, /auth/register, /auth/me, /auth/logout
- Google OAuth: /auth/google, /auth/google/callback

**Frontend:**
- Redux auth slice with login/logout actions
- Login component with Google button and local form
- AuthCallback for OAuth redirect handling
- App integration with auth state

**Configuration:**
- `LOCAL_AUTH_ENABLED=true/false` to toggle local auth
- `GOOGLE_AUTH_ENABLED=true/false` to toggle Google auth
- JWT settings configurable via environment variables
