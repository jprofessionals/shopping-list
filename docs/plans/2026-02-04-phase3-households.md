# Phase 3: Households Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement full Household CRUD operations with member management, allowing users to create households, invite members, and manage roles.

**Architecture:** Backend provides REST API endpoints for household operations with JWT authentication. Frontend displays household list, creation modal, and detail view with member management. Permission checks ensure only owners can delete households or change member roles.

**Tech Stack:**
- Backend: Ktor routes, HouseholdService, Exposed ORM, JWT auth
- Web: Redux Toolkit slice, React components with TailwindCSS

---

## Phase Overview

| Task | Description |
|------|-------------|
| 1 | Create HouseholdService |
| 2 | Create HouseholdRoutes (CRUD) |
| 3 | Add member management routes |
| 4 | Add household route tests |
| 5 | Create households Redux slice |
| 6 | Create HouseholdList component |
| 7 | Create CreateHouseholdModal |
| 8 | Create HouseholdDetail component |
| 9 | Integrate households into App |
| 10 | Final verification |

---

## Task 1: Create HouseholdService

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/HouseholdService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/HouseholdServiceTest.kt`

**Step 1: Write failing test**

Create `backend/src/test/kotlin/no/shoppinglist/service/HouseholdServiceTest.kt`:

```kotlin
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
import no.shoppinglist.domain.MembershipRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class HouseholdServiceTest : FunSpec({
    lateinit var db: Database
    lateinit var householdService: HouseholdService
    lateinit var testAccountId: UUID

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships)
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
        transaction(db) {
            SchemaUtils.drop(HouseholdMemberships, Households, Accounts)
        }
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
```

**Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.HouseholdServiceTest"`
Expected: FAIL - HouseholdService not defined

**Step 3: Create HouseholdService**

Create `backend/src/main/kotlin/no/shoppinglist/service/HouseholdService.kt`:

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.Household
import no.shoppinglist.domain.HouseholdMembership
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.domain.Account
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class MemberInfo(
    val accountId: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: MembershipRole,
    val joinedAt: Instant
)

class HouseholdService(private val db: Database) {

    fun create(name: String, ownerId: UUID): Household {
        return transaction(db) {
            val household = Household.new {
                this.name = name
                this.createdAt = Instant.now()
            }

            val owner = Account.findById(ownerId)
                ?: throw IllegalArgumentException("Account not found: $ownerId")

            HouseholdMembership.new {
                this.account = owner
                this.household = household
                this.role = MembershipRole.OWNER
                this.joinedAt = Instant.now()
            }

            household
        }
    }

    fun findById(id: UUID): Household? {
        return transaction(db) {
            Household.findById(id)
        }
    }

    fun findByAccountId(accountId: UUID): List<Household> {
        return transaction(db) {
            HouseholdMembership
                .find { HouseholdMemberships.account eq accountId }
                .map { it.household }
        }
    }

    fun update(id: UUID, name: String): Household? {
        return transaction(db) {
            val household = Household.findById(id) ?: return@transaction null
            household.name = name
            household
        }
    }

    fun delete(id: UUID): Boolean {
        return transaction(db) {
            HouseholdMemberships.deleteWhere { household eq id }
            val deleted = Households.deleteWhere { Households.id eq id }
            deleted > 0
        }
    }

    fun getMembers(householdId: UUID): List<MemberInfo> {
        return transaction(db) {
            HouseholdMembership
                .find { HouseholdMemberships.household eq householdId }
                .map { membership ->
                    MemberInfo(
                        accountId = membership.account.id.value,
                        email = membership.account.email,
                        displayName = membership.account.displayName,
                        avatarUrl = membership.account.avatarUrl,
                        role = membership.role,
                        joinedAt = membership.joinedAt
                    )
                }
        }
    }

    fun isOwner(householdId: UUID, accountId: UUID): Boolean {
        return transaction(db) {
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.account eq accountId) and
                        (HouseholdMemberships.role eq MembershipRole.OWNER)
                }
                .firstOrNull() != null
        }
    }

    fun isMember(householdId: UUID, accountId: UUID): Boolean {
        return transaction(db) {
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.account eq accountId)
                }
                .firstOrNull() != null
        }
    }

    fun addMember(householdId: UUID, accountId: UUID, role: MembershipRole): HouseholdMembership? {
        return transaction(db) {
            val household = Household.findById(householdId) ?: return@transaction null
            val account = Account.findById(accountId) ?: return@transaction null

            if (isMemberInternal(householdId, accountId)) {
                return@transaction null
            }

            HouseholdMembership.new {
                this.account = account
                this.household = household
                this.role = role
                this.joinedAt = Instant.now()
            }
        }
    }

    fun removeMember(householdId: UUID, accountId: UUID): Boolean {
        return transaction(db) {
            val deleted = HouseholdMemberships.deleteWhere {
                (household eq householdId) and (account eq accountId)
            }
            deleted > 0
        }
    }

    fun updateMemberRole(householdId: UUID, accountId: UUID, newRole: MembershipRole): Boolean {
        return transaction(db) {
            val membership = HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.account eq accountId)
                }
                .firstOrNull() ?: return@transaction false

            membership.role = newRole
            true
        }
    }

    private fun isMemberInternal(householdId: UUID, accountId: UUID): Boolean {
        return HouseholdMembership
            .find {
                (HouseholdMemberships.household eq householdId) and
                    (HouseholdMemberships.account eq accountId)
            }
            .firstOrNull() != null
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "no.shoppinglist.service.HouseholdServiceTest"`
Expected: PASS

**Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 6: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/HouseholdService.kt backend/src/test/kotlin/no/shoppinglist/service/HouseholdServiceTest.kt
git commit -m "feat: add HouseholdService with CRUD and member management"
```

---

## Task 2: Create HouseholdRoutes (CRUD)

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/HouseholdRoutes.kt`
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Create HouseholdRoutes**

Create `backend/src/main/kotlin/no/shoppinglist/routes/HouseholdRoutes.kt`:

```kotlin
package no.shoppinglist.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import no.shoppinglist.domain.MembershipRole
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.MemberInfo
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class UpdateHouseholdRequest(val name: String)

@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val memberCount: Int,
    val isOwner: Boolean
)

@Serializable
data class HouseholdDetailResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val members: List<MemberResponse>
)

@Serializable
data class MemberResponse(
    val accountId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val joinedAt: String
)

@Serializable
data class AddMemberRequest(val email: String, val role: String = "MEMBER")

@Serializable
data class UpdateMemberRoleRequest(val role: String)

fun Route.householdRoutes(
    householdService: HouseholdService,
    accountService: AccountService
) {
    authenticate("auth-jwt") {
        route("/households") {
            get {
                val accountId = call.principal<JWTPrincipal>()?.subject
                    ?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val households = householdService.findByAccountId(accountId)
                val response = households.map { household ->
                    val members = householdService.getMembers(household.id.value)
                    val isOwner = householdService.isOwner(household.id.value, accountId)
                    transaction {
                        HouseholdResponse(
                            id = household.id.value.toString(),
                            name = household.name,
                            createdAt = household.createdAt.toString(),
                            memberCount = members.size,
                            isOwner = isOwner
                        )
                    }
                }
                call.respond(response)
            }

            post {
                val accountId = call.principal<JWTPrincipal>()?.subject
                    ?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<CreateHouseholdRequest>()
                val household = householdService.create(request.name, accountId)

                call.respond(
                    HttpStatusCode.Created,
                    transaction {
                        HouseholdResponse(
                            id = household.id.value.toString(),
                            name = household.name,
                            createdAt = household.createdAt.toString(),
                            memberCount = 1,
                            isOwner = true
                        )
                    }
                )
            }

            route("/{id}") {
                get {
                    val accountId = call.principal<JWTPrincipal>()?.subject
                        ?.let { UUID.fromString(it) }
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val householdId = call.parameters["id"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                    if (!householdService.isMember(householdId, accountId)) {
                        return@get call.respond(HttpStatusCode.Forbidden)
                    }

                    val household = householdService.findById(householdId)
                        ?: return@get call.respond(HttpStatusCode.NotFound)

                    val members = householdService.getMembers(householdId)

                    call.respond(
                        transaction {
                            HouseholdDetailResponse(
                                id = household.id.value.toString(),
                                name = household.name,
                                createdAt = household.createdAt.toString(),
                                members = members.map { it.toResponse() }
                            )
                        }
                    )
                }

                patch {
                    val accountId = call.principal<JWTPrincipal>()?.subject
                        ?.let { UUID.fromString(it) }
                        ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                    val householdId = call.parameters["id"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@patch call.respond(HttpStatusCode.BadRequest)

                    if (!householdService.isMember(householdId, accountId)) {
                        return@patch call.respond(HttpStatusCode.Forbidden)
                    }

                    val request = call.receive<UpdateHouseholdRequest>()
                    val updated = householdService.update(householdId, request.name)
                        ?: return@patch call.respond(HttpStatusCode.NotFound)

                    val members = householdService.getMembers(householdId)
                    val isOwner = householdService.isOwner(householdId, accountId)

                    call.respond(
                        transaction {
                            HouseholdResponse(
                                id = updated.id.value.toString(),
                                name = updated.name,
                                createdAt = updated.createdAt.toString(),
                                memberCount = members.size,
                                isOwner = isOwner
                            )
                        }
                    )
                }

                delete {
                    val accountId = call.principal<JWTPrincipal>()?.subject
                        ?.let { UUID.fromString(it) }
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                    val householdId = call.parameters["id"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    if (!householdService.isOwner(householdId, accountId)) {
                        return@delete call.respond(HttpStatusCode.Forbidden)
                    }

                    householdService.delete(householdId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun MemberInfo.toResponse() = MemberResponse(
    accountId = accountId.toString(),
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = role.name,
    joinedAt = joinedAt.toString()
)
```

**Step 2: Update Application.kt**

Read the current Application.kt and add:
1. Instantiate HouseholdService after AccountService
2. Add householdRoutes to the routing block

Add after `val accountService = AccountService(db)`:
```kotlin
val householdService = HouseholdService(db)
```

Add in routing block after `authRoutes(...)`:
```kotlin
householdRoutes(householdService, accountService)
```

**Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/HouseholdRoutes.kt backend/src/main/kotlin/no/shoppinglist/Application.kt
git commit -m "feat: add household CRUD routes"
```

---

## Task 3: Add Member Management Routes

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/HouseholdRoutes.kt`

**Step 1: Add member routes**

Add inside the `route("/{id}")` block in HouseholdRoutes.kt:

```kotlin
route("/members") {
    post {
        val accountId = call.principal<JWTPrincipal>()?.subject
            ?.let { UUID.fromString(it) }
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val householdId = call.parameters["id"]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (!householdService.isOwner(householdId, accountId)) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val request = call.receive<AddMemberRequest>()
        val targetAccount = accountService.findByEmail(request.email)
            ?: return@post call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "User not found")
            )

        val role = runCatching { MembershipRole.valueOf(request.role) }
            .getOrElse { MembershipRole.MEMBER }

        val membership = householdService.addMember(
            householdId,
            targetAccount.id.value,
            role
        )

        if (membership == null) {
            return@post call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "User is already a member")
            )
        }

        call.respond(HttpStatusCode.Created, mapOf("message" to "Member added"))
    }

    route("/{accountId}") {
        patch {
            val currentAccountId = call.principal<JWTPrincipal>()?.subject
                ?.let { UUID.fromString(it) }
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)

            val householdId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

            val targetAccountId = call.parameters["accountId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

            if (!householdService.isOwner(householdId, currentAccountId)) {
                return@patch call.respond(HttpStatusCode.Forbidden)
            }

            val request = call.receive<UpdateMemberRoleRequest>()
            val newRole = runCatching { MembershipRole.valueOf(request.role) }
                .getOrElse {
                    return@patch call.respond(HttpStatusCode.BadRequest)
                }

            val updated = householdService.updateMemberRole(
                householdId,
                targetAccountId,
                newRole
            )

            if (!updated) {
                return@patch call.respond(HttpStatusCode.NotFound)
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "Role updated"))
        }

        delete {
            val currentAccountId = call.principal<JWTPrincipal>()?.subject
                ?.let { UUID.fromString(it) }
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)

            val householdId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            val targetAccountId = call.parameters["accountId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            val isOwner = householdService.isOwner(householdId, currentAccountId)
            val isSelf = currentAccountId == targetAccountId

            if (!isOwner && !isSelf) {
                return@delete call.respond(HttpStatusCode.Forbidden)
            }

            val removed = householdService.removeMember(householdId, targetAccountId)
            if (!removed) {
                return@delete call.respond(HttpStatusCode.NotFound)
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
```

**Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 3: Run linting**

Run: `./gradlew detekt ktlintCheck`
If needed: `./gradlew ktlintFormat`

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/HouseholdRoutes.kt
git commit -m "feat: add member management routes"
```

---

## Task 4: Add Household Route Tests

**Files:**
- Create: `backend/src/test/kotlin/no/shoppinglist/routes/HouseholdRoutesTest.kt`

**Step 1: Create test file**

Create `backend/src/test/kotlin/no/shoppinglist/routes/HouseholdRoutesTest.kt`:

```kotlin
package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class HouseholdRoutesTest : FunSpec({
    lateinit var db: Database

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships)
        }
    }

    afterTest {
        transaction(db) {
            HouseholdMemberships.deleteAll()
            Households.deleteAll()
            Accounts.deleteAll()
        }
    }

    afterSpec {
        transaction(db) {
            SchemaUtils.drop(HouseholdMemberships, Households, Accounts)
        }
    }

    test("GET /households returns empty list for new user") {
        testApplication {
            application { testModule(db) }

            val token = registerAndGetToken(client, "test@example.com")

            val response = client.get("/households") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            json.size shouldBe 0
        }
    }

    test("POST /households creates household") {
        testApplication {
            application { testModule(db) }

            val token = registerAndGetToken(client, "test@example.com")

            val response = client.post("/households") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"My Home"}""")
            }

            response.status shouldBe HttpStatusCode.Created
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["name"]?.jsonPrimitive?.content shouldBe "My Home"
            json["isOwner"]?.jsonPrimitive?.content shouldBe "true"
        }
    }

    test("GET /households returns created households") {
        testApplication {
            application { testModule(db) }

            val token = registerAndGetToken(client, "test@example.com")

            client.post("/households") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Home"}""")
            }

            val response = client.get("/households") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            json.size shouldBe 1
        }
    }

    test("DELETE /households/:id requires owner") {
        testApplication {
            application { testModule(db) }

            val ownerToken = registerAndGetToken(client, "owner@example.com")
            val memberToken = registerAndGetToken(client, "member@example.com")

            val createResponse = client.post("/households") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Test"}""")
            }
            val householdId = Json.parseToJsonElement(createResponse.bodyAsText())
                .jsonObject["id"]?.jsonPrimitive?.content

            val deleteResponse = client.delete("/households/$householdId") {
                header(HttpHeaders.Authorization, "Bearer $memberToken")
            }

            deleteResponse.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("GET /households returns 401 without auth") {
        testApplication {
            application { testModule(db) }

            val response = client.get("/households")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private suspend fun registerAndGetToken(
    client: io.ktor.client.HttpClient,
    email: String
): String {
    val response = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","displayName":"Test User","password":"password123"}""")
    }
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["token"]?.jsonPrimitive?.content ?: throw Exception("No token in response")
}
```

**Step 2: Create testModule helper**

Add to `backend/src/test/kotlin/no/shoppinglist/routes/AuthRoutesTest.kt` or create a shared test utility. Ensure the `testModule` function sets up HouseholdService and routes.

**Step 3: Run tests**

Run: `./gradlew test --tests "no.shoppinglist.routes.HouseholdRoutesTest"`
Expected: PASS

**Step 4: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 5: Commit**

```bash
git add backend/src/test/kotlin/no/shoppinglist/routes/HouseholdRoutesTest.kt
git commit -m "test: add household route tests"
```

---

## Task 5: Create Households Redux Slice

**Files:**
- Create: `web/src/store/householdsSlice.ts`
- Create: `web/src/store/householdsSlice.test.ts`
- Modify: `web/src/store/store.ts`

**Step 1: Write failing test**

Create `web/src/store/householdsSlice.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import householdsReducer, {
  setHouseholds,
  addHousehold,
  updateHousehold,
  removeHousehold,
  setLoading,
  setError,
  type HouseholdsState,
  type Household,
} from './householdsSlice';

describe('householdsSlice', () => {
  const initialState: HouseholdsState = {
    items: [],
    isLoading: false,
    error: null,
  };

  const mockHousehold: Household = {
    id: '1',
    name: 'Home',
    createdAt: '2024-01-01T00:00:00Z',
    memberCount: 2,
    isOwner: true,
  };

  it('should handle initial state', () => {
    expect(householdsReducer(undefined, { type: 'unknown' })).toEqual(initialState);
  });

  it('should handle setHouseholds', () => {
    const state = householdsReducer(initialState, setHouseholds([mockHousehold]));
    expect(state.items).toHaveLength(1);
    expect(state.items[0].name).toBe('Home');
  });

  it('should handle addHousehold', () => {
    const state = householdsReducer(initialState, addHousehold(mockHousehold));
    expect(state.items).toHaveLength(1);
  });

  it('should handle updateHousehold', () => {
    const stateWithHousehold = { ...initialState, items: [mockHousehold] };
    const state = householdsReducer(
      stateWithHousehold,
      updateHousehold({ id: '1', name: 'New Name' })
    );
    expect(state.items[0].name).toBe('New Name');
  });

  it('should handle removeHousehold', () => {
    const stateWithHousehold = { ...initialState, items: [mockHousehold] };
    const state = householdsReducer(stateWithHousehold, removeHousehold('1'));
    expect(state.items).toHaveLength(0);
  });

  it('should handle setLoading', () => {
    const state = householdsReducer(initialState, setLoading(true));
    expect(state.isLoading).toBe(true);
  });

  it('should handle setError', () => {
    const state = householdsReducer(initialState, setError('Something went wrong'));
    expect(state.error).toBe('Something went wrong');
  });
});
```

**Step 2: Create householdsSlice**

Create `web/src/store/householdsSlice.ts`:

```typescript
import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface Household {
  id: string;
  name: string;
  createdAt: string;
  memberCount: number;
  isOwner: boolean;
}

export interface HouseholdsState {
  items: Household[];
  isLoading: boolean;
  error: string | null;
}

const initialState: HouseholdsState = {
  items: [],
  isLoading: false,
  error: null,
};

const householdsSlice = createSlice({
  name: 'households',
  initialState,
  reducers: {
    setHouseholds(state, action: PayloadAction<Household[]>) {
      state.items = action.payload;
      state.error = null;
    },
    addHousehold(state, action: PayloadAction<Household>) {
      state.items.push(action.payload);
    },
    updateHousehold(state, action: PayloadAction<{ id: string; name: string }>) {
      const household = state.items.find((h) => h.id === action.payload.id);
      if (household) {
        household.name = action.payload.name;
      }
    },
    removeHousehold(state, action: PayloadAction<string>) {
      state.items = state.items.filter((h) => h.id !== action.payload);
    },
    setLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
    setError(state, action: PayloadAction<string | null>) {
      state.error = action.payload;
    },
  },
});

export const {
  setHouseholds,
  addHousehold,
  updateHousehold,
  removeHousehold,
  setLoading,
  setError,
} = householdsSlice.actions;

export default householdsSlice.reducer;
```

**Step 3: Update store.ts**

Add households reducer to the store:

```typescript
import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import householdsReducer from './householdsSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    households: householdsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

**Step 4: Run tests**

Run: `cd web && npm run test:run`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/store/householdsSlice.ts web/src/store/householdsSlice.test.ts web/src/store/store.ts
git commit -m "feat: add households Redux slice"
```

---

## Task 6: Create HouseholdList Component

**Files:**
- Create: `web/src/components/HouseholdList.tsx`
- Create: `web/src/components/HouseholdList.test.tsx`

**Step 1: Write failing test**

Create `web/src/components/HouseholdList.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import HouseholdList from './HouseholdList';
import householdsReducer from '../store/householdsSlice';
import authReducer from '../store/authSlice';

const createTestStore = (preloadedState = {}) =>
  configureStore({
    reducer: { households: householdsReducer, auth: authReducer },
    preloadedState,
  });

describe('HouseholdList', () => {
  it('renders empty state when no households', () => {
    render(
      <Provider store={createTestStore()}>
        <HouseholdList onCreateClick={() => {}} onSelectHousehold={() => {}} />
      </Provider>
    );

    expect(screen.getByText(/no households yet/i)).toBeInTheDocument();
  });

  it('renders list of households', () => {
    const store = createTestStore({
      households: {
        items: [
          { id: '1', name: 'Home', createdAt: '2024-01-01', memberCount: 2, isOwner: true },
          { id: '2', name: 'Cabin', createdAt: '2024-01-02', memberCount: 1, isOwner: false },
        ],
        isLoading: false,
        error: null,
      },
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(
      <Provider store={store}>
        <HouseholdList onCreateClick={() => {}} onSelectHousehold={() => {}} />
      </Provider>
    );

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText('Cabin')).toBeInTheDocument();
  });

  it('shows create button', () => {
    render(
      <Provider store={createTestStore()}>
        <HouseholdList onCreateClick={() => {}} onSelectHousehold={() => {}} />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /create household/i })).toBeInTheDocument();
  });
});
```

**Step 2: Create HouseholdList component**

Create `web/src/components/HouseholdList.tsx`:

```typescript
import { useAppSelector } from '../store/hooks';

interface HouseholdListProps {
  onCreateClick: () => void;
  onSelectHousehold: (id: string) => void;
}

export default function HouseholdList({ onCreateClick, onSelectHousehold }: HouseholdListProps) {
  const { items, isLoading } = useAppSelector((state) => state.households);

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">Your Households</h2>
        <button
          onClick={onCreateClick}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          Create Household
        </button>
      </div>

      {items.length === 0 ? (
        <div className="rounded-lg border-2 border-dashed border-gray-300 p-12 text-center">
          <h3 className="text-sm font-semibold text-gray-900">No households yet</h3>
          <p className="mt-1 text-sm text-gray-500">
            Create a household to start managing shopping lists with your family.
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
          >
            Create your first household
          </button>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((household) => (
            <button
              key={household.id}
              onClick={() => onSelectHousehold(household.id)}
              className="rounded-lg bg-white p-6 text-left shadow transition hover:shadow-md"
            >
              <div className="flex items-start justify-between">
                <h3 className="font-semibold text-gray-900">{household.name}</h3>
                {household.isOwner && (
                  <span className="rounded-full bg-indigo-100 px-2 py-1 text-xs font-medium text-indigo-700">
                    Owner
                  </span>
                )}
              </div>
              <p className="mt-2 text-sm text-gray-500">
                {household.memberCount} {household.memberCount === 1 ? 'member' : 'members'}
              </p>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
```

**Step 3: Run tests**

Run: `npm run test:run`
Expected: PASS

**Step 4: Commit**

```bash
git add web/src/components/HouseholdList.tsx web/src/components/HouseholdList.test.tsx
git commit -m "feat: add HouseholdList component"
```

---

## Task 7: Create CreateHouseholdModal

**Files:**
- Create: `web/src/components/CreateHouseholdModal.tsx`
- Create: `web/src/components/CreateHouseholdModal.test.tsx`

**Step 1: Write failing test**

Create `web/src/components/CreateHouseholdModal.test.tsx`:

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import CreateHouseholdModal from './CreateHouseholdModal';
import householdsReducer from '../store/householdsSlice';
import authReducer from '../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { households: householdsReducer, auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    },
  });

describe('CreateHouseholdModal', () => {
  it('renders form when open', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={true} onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByLabelText(/household name/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument();
  });

  it('does not render when closed', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={false} onClose={() => {}} />
      </Provider>
    );

    expect(screen.queryByLabelText(/household name/i)).not.toBeInTheDocument();
  });

  it('calls onClose when cancel clicked', () => {
    const onClose = vi.fn();
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={true} onClose={onClose} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
```

**Step 2: Create CreateHouseholdModal component**

Create `web/src/components/CreateHouseholdModal.tsx`:

```typescript
import { useState } from 'react';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { addHousehold, setError } from '../store/householdsSlice';

interface CreateHouseholdModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CreateHouseholdModal({ isOpen, onClose }: CreateHouseholdModalProps) {
  const [name, setName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !token) return;

    setIsSubmitting(true);
    try {
      const response = await fetch('http://localhost:8080/households', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ name: name.trim() }),
      });

      if (!response.ok) {
        throw new Error('Failed to create household');
      }

      const household = await response.json();
      dispatch(addHousehold(household));
      setName('');
      onClose();
    } catch (err) {
      dispatch(setError(err instanceof Error ? err.message : 'Failed to create household'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
        <div
          className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"
          onClick={onClose}
        />

        <div className="relative transform overflow-hidden rounded-lg bg-white px-4 pb-4 pt-5 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6">
          <form onSubmit={handleSubmit}>
            <div>
              <h3 className="text-lg font-semibold leading-6 text-gray-900">
                Create New Household
              </h3>
              <div className="mt-4">
                <label
                  htmlFor="householdName"
                  className="block text-sm font-medium leading-6 text-gray-900"
                >
                  Household Name
                </label>
                <input
                  type="text"
                  id="householdName"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g., Home, Cabin, Office"
                  className="mt-2 block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                  required
                />
              </div>
            </div>
            <div className="mt-5 sm:mt-6 sm:grid sm:grid-flow-row-dense sm:grid-cols-2 sm:gap-3">
              <button
                type="submit"
                disabled={isSubmitting || !name.trim()}
                className="inline-flex w-full justify-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 sm:col-start-2"
              >
                {isSubmitting ? 'Creating...' : 'Create'}
              </button>
              <button
                type="button"
                onClick={onClose}
                className="mt-3 inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:col-start-1 sm:mt-0"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
```

**Step 3: Run tests**

Run: `npm run test:run`
Expected: PASS

**Step 4: Commit**

```bash
git add web/src/components/CreateHouseholdModal.tsx web/src/components/CreateHouseholdModal.test.tsx
git commit -m "feat: add CreateHouseholdModal component"
```

---

## Task 8: Create HouseholdDetail Component

**Files:**
- Create: `web/src/components/HouseholdDetail.tsx`
- Create: `web/src/components/HouseholdDetail.test.tsx`

**Step 1: Write failing test**

Create `web/src/components/HouseholdDetail.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import HouseholdDetail from './HouseholdDetail';
import householdsReducer from '../store/householdsSlice';
import authReducer from '../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { households: householdsReducer, auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    },
  });

describe('HouseholdDetail', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              id: '1',
              name: 'Home',
              createdAt: '2024-01-01',
              members: [
                {
                  accountId: '1',
                  email: 'test@example.com',
                  displayName: 'Test User',
                  avatarUrl: null,
                  role: 'OWNER',
                  joinedAt: '2024-01-01',
                },
              ],
            }),
        })
      ) as unknown as typeof fetch
    );
  });

  it('renders household name', async () => {
    render(
      <Provider store={createTestStore()}>
        <HouseholdDetail householdId="1" onBack={() => {}} />
      </Provider>
    );

    expect(await screen.findByText('Home')).toBeInTheDocument();
  });

  it('renders member list', async () => {
    render(
      <Provider store={createTestStore()}>
        <HouseholdDetail householdId="1" onBack={() => {}} />
      </Provider>
    );

    expect(await screen.findByText('Test User')).toBeInTheDocument();
  });

  it('shows back button', () => {
    render(
      <Provider store={createTestStore()}>
        <HouseholdDetail householdId="1" onBack={() => {}} />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
  });
});
```

**Step 2: Create HouseholdDetail component**

Create `web/src/components/HouseholdDetail.tsx`:

```typescript
import { useEffect, useState } from 'react';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { removeHousehold } from '../store/householdsSlice';

interface Member {
  accountId: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: string;
  joinedAt: string;
}

interface HouseholdDetailData {
  id: string;
  name: string;
  createdAt: string;
  members: Member[];
}

interface HouseholdDetailProps {
  householdId: string;
  onBack: () => void;
}

export default function HouseholdDetail({ householdId, onBack }: HouseholdDetailProps) {
  const [household, setHousehold] = useState<HouseholdDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const token = useAppSelector((state) => state.auth.token);
  const currentUser = useAppSelector((state) => state.auth.user);
  const dispatch = useAppDispatch();

  useEffect(() => {
    const fetchHousehold = async () => {
      if (!token) return;

      try {
        const response = await fetch(`http://localhost:8080/households/${householdId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!response.ok) throw new Error('Failed to fetch household');

        const data = await response.json();
        setHousehold(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setIsLoading(false);
      }
    };

    fetchHousehold();
  }, [householdId, token]);

  const handleDelete = async () => {
    if (!token || !confirm('Are you sure you want to delete this household?')) return;

    try {
      const response = await fetch(`http://localhost:8080/households/${householdId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!response.ok) throw new Error('Failed to delete household');

      dispatch(removeHousehold(householdId));
      onBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete');
    }
  };

  const isOwner = household?.members.some(
    (m) => m.accountId === currentUser?.id && m.role === 'OWNER'
  );

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
      </div>
    );
  }

  if (error || !household) {
    return (
      <div className="rounded-md bg-red-50 p-4">
        <p className="text-sm text-red-700">{error || 'Household not found'}</p>
        <button onClick={onBack} className="mt-2 text-sm text-red-600 underline">
          Go back
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <button
          onClick={onBack}
          className="flex items-center text-sm text-gray-500 hover:text-gray-700"
        >
          <span className="mr-1">←</span> Back to households
        </button>
      </div>

      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{household.name}</h2>
        {isOwner && (
          <button
            onClick={handleDelete}
            className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-500"
          >
            Delete Household
          </button>
        )}
      </div>

      <div className="rounded-lg bg-white p-6 shadow">
        <h3 className="mb-4 text-lg font-semibold text-gray-900">
          Members ({household.members.length})
        </h3>
        <ul className="divide-y divide-gray-200">
          {household.members.map((member) => (
            <li key={member.accountId} className="flex items-center justify-between py-4">
              <div className="flex items-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gray-200 text-sm font-medium text-gray-600">
                  {member.displayName.charAt(0).toUpperCase()}
                </div>
                <div className="ml-3">
                  <p className="text-sm font-medium text-gray-900">{member.displayName}</p>
                  <p className="text-sm text-gray-500">{member.email}</p>
                </div>
              </div>
              <span
                className={`rounded-full px-2 py-1 text-xs font-medium ${
                  member.role === 'OWNER'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'bg-gray-100 text-gray-700'
                }`}
              >
                {member.role}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
```

**Step 3: Run tests**

Run: `npm run test:run`
Expected: PASS

**Step 4: Commit**

```bash
git add web/src/components/HouseholdDetail.tsx web/src/components/HouseholdDetail.test.tsx
git commit -m "feat: add HouseholdDetail component with member list"
```

---

## Task 9: Integrate Households into App

**Files:**
- Modify: `web/src/App.tsx`

**Step 1: Update App.tsx**

Read current App.tsx and update to include:
1. State for selected household and modal visibility
2. Fetch households on mount when authenticated
3. Render HouseholdList or HouseholdDetail based on selection
4. Render CreateHouseholdModal

Add imports:
```typescript
import HouseholdList from './components/HouseholdList';
import HouseholdDetail from './components/HouseholdDetail';
import CreateHouseholdModal from './components/CreateHouseholdModal';
import { setHouseholds, setLoading } from './store/householdsSlice';
```

Add state:
```typescript
const [selectedHouseholdId, setSelectedHouseholdId] = useState<string | null>(null);
const [showCreateModal, setShowCreateModal] = useState(false);
```

Add effect to fetch households when authenticated:
```typescript
useEffect(() => {
  if (isAuthenticated && token) {
    dispatch(setLoading(true));
    fetch('http://localhost:8080/households', {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => res.json())
      .then((data) => dispatch(setHouseholds(data)))
      .catch(console.error)
      .finally(() => dispatch(setLoading(false)));
  }
}, [isAuthenticated, token, dispatch]);
```

Update the main content area to show households:
```typescript
<main>
  <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
    {selectedHouseholdId ? (
      <HouseholdDetail
        householdId={selectedHouseholdId}
        onBack={() => setSelectedHouseholdId(null)}
      />
    ) : (
      <HouseholdList
        onCreateClick={() => setShowCreateModal(true)}
        onSelectHousehold={setSelectedHouseholdId}
      />
    )}
  </div>
</main>

<CreateHouseholdModal
  isOpen={showCreateModal}
  onClose={() => setShowCreateModal(false)}
/>
```

**Step 2: Run tests**

Run: `npm run test:run`
Expected: PASS (may need to update App.test.tsx)

**Step 3: Run lint**

Run: `npm run lint`
If needed: `npm run lint:fix`

**Step 4: Commit**

```bash
git add web/src/App.tsx
git commit -m "feat: integrate households into main App"
```

---

## Task 10: Final Verification

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
1. Households list renders
2. Create household modal works
3. Household detail shows members

**Step 5: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final verification and fixes for Phase 3 households"
```

---

## Summary

Phase 3 implements:

**Backend:**
- HouseholdService with CRUD operations
- Member management (add, remove, change role)
- Permission checks (owner-only operations)
- HouseholdRoutes with JWT authentication
- Route tests

**Frontend:**
- Households Redux slice
- HouseholdList component with empty state
- CreateHouseholdModal
- HouseholdDetail with member list
- App integration

**API Endpoints:**
- `GET /households` - List user's households
- `POST /households` - Create household
- `GET /households/:id` - Get household with members
- `PATCH /households/:id` - Update household name
- `DELETE /households/:id` - Delete household (owners only)
- `POST /households/:id/members` - Add member
- `PATCH /households/:id/members/:accountId` - Change member role
- `DELETE /households/:id/members/:accountId` - Remove member
