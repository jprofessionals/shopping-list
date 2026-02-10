# Phase 1: Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Set up backend (Ktor + PostgreSQL) and web (React + Redux) project foundations with linting, testing infrastructure, and CI.

**Architecture:** Monorepo with `backend/` and `web/` directories. Backend uses Ktor with Exposed ORM for PostgreSQL. Web uses React 18 with Redux Toolkit and TypeScript.

**Tech Stack:**
- Backend: Kotlin, Ktor, Exposed, PostgreSQL, Detekt, ktlint, Kotest, Testcontainers
- Web: TypeScript, React 18, Redux Toolkit, ESLint, Prettier, Vitest, React Testing Library
- Infrastructure: Docker Compose for local PostgreSQL

---

## Phase Overview

| Phase | Description | Status |
|-------|-------------|--------|
| **1. Foundation** | Project setup, tooling, CI | This plan |
| 2. Authentication | Google OAuth backend + web login | Planned after Phase 1 |
| 3. Households | Household CRUD vertical slice | Planned after Phase 2 |
| 4. Lists & Items | Shopping list vertical slice | Planned after Phase 3 |
| 5. Real-time Sync | WebSocket integration | Planned after Phase 4 |
| 6. Recurring Items | Scheduled item creation | Planned after Phase 5 |
| 7. Sharing | User/link sharing | Planned after Phase 6 |
| 8. Android App | Native Kotlin client | Planned after Phase 7 |

---

## Task 1: Repository Structure

**Files:**
- Create: `backend/.gitkeep`
- Create: `web/.gitkeep`
- Create: `docker-compose.yml`
- Create: `README.md`
- Modify: `.gitignore`

**Step 1: Create directory structure**

```bash
mkdir -p backend web
```

**Step 2: Create docker-compose.yml for PostgreSQL**

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  db:
    image: postgres:16-alpine
    container_name: shopping-list-db
    environment:
      POSTGRES_USER: shopping
      POSTGRES_PASSWORD: shopping_dev
      POSTGRES_DB: shopping_list
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

**Step 3: Update .gitignore**

Append to `.gitignore`:

```gitignore
# IDE
.idea/
*.iml
.vscode/

# Build outputs
backend/build/
web/node_modules/
web/dist/

# Environment
.env
.env.local

# OS
.DS_Store
Thumbs.db
```

**Step 4: Create README.md**

Create `README.md`:

```markdown
# Shopping List

A shared household shopping list application.

## Prerequisites

- Docker and Docker Compose
- JDK 21
- Node.js 20+

## Development Setup

### Start database

```bash
docker-compose up -d
```

### Backend

```bash
cd backend
./gradlew run
```

### Web

```bash
cd web
npm install
npm run dev
```

## Project Structure

- `backend/` - Kotlin/Ktor API server
- `web/` - React/TypeScript web application
- `docs/plans/` - Design and implementation documents
```

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: add repository structure and docker-compose"
```

---

## Task 2: Backend Project Setup (Ktor + Gradle)

**Files:**
- Create: `backend/build.gradle.kts`
- Create: `backend/settings.gradle.kts`
- Create: `backend/gradle.properties`
- Create: `backend/src/main/resources/application.conf`
- Create: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Create settings.gradle.kts**

Create `backend/settings.gradle.kts`:

```kotlin
rootProject.name = "shopping-list-backend"
```

**Step 2: Create gradle.properties**

Create `backend/gradle.properties`:

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m
```

**Step 3: Create build.gradle.kts**

Create `backend/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "no.shoppinglist"
version = "0.0.1"

application {
    mainClass.set("no.shoppinglist.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.7"
val exposedVersion = "0.46.0"
val postgresVersion = "42.7.1"
val logbackVersion = "1.4.14"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

detekt {
    config.setFrom(files("$projectDir/detekt.yml"))
    buildUponDefaultConfig = true
}

ktlint {
    version.set("1.1.1")
}
```

**Step 4: Create application.conf**

Create `backend/src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ no.shoppinglist.ApplicationKt.module ]
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/shopping_list"
    url = ${?DATABASE_URL}
    user = "shopping"
    user = ${?DATABASE_USER}
    password = "shopping_dev"
    password = ${?DATABASE_PASSWORD}
}
```

**Step 5: Create Application.kt**

Create `backend/src/main/kotlin/no/shoppinglist/Application.kt`:

```kotlin
package no.shoppinglist

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
```

**Step 6: Create Gradle wrapper**

```bash
cd backend
gradle wrapper --gradle-version 8.5
cd ..
```

**Step 7: Verify backend compiles**

```bash
cd backend
./gradlew build -x test
cd ..
```

Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add -A
git commit -m "feat: add Ktor backend project structure"
```

---

## Task 3: Backend Detekt Configuration

**Files:**
- Create: `backend/detekt.yml`

**Step 1: Create detekt.yml**

Create `backend/detekt.yml`:

```yaml
build:
  maxIssues: 0

complexity:
  LongMethod:
    threshold: 30
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 10
  TooManyFunctions:
    thresholdInFiles: 20
    thresholdInClasses: 15

style:
  MaxLineLength:
    maxLineLength: 120
  WildcardImport:
    active: true

formatting:
  active: true
  android: false
```

**Step 2: Run detekt**

```bash
cd backend
./gradlew detekt
cd ..
```

Expected: BUILD SUCCESSFUL (no issues)

**Step 3: Run ktlint**

```bash
cd backend
./gradlew ktlintCheck
cd ..
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add backend/detekt.yml
git commit -m "feat: add detekt configuration"
```

---

## Task 4: Backend Health Endpoint Test

**Files:**
- Create: `backend/src/test/kotlin/no/shoppinglist/HealthRouteTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/HealthRouteTest.kt`:

```kotlin
package no.shoppinglist

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class HealthRouteTest : FunSpec({

    test("health endpoint returns OK") {
        testApplication {
            application {
                module()
            }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK"
        }
    }
})
```

**Step 2: Run test to verify it passes**

```bash
cd backend
./gradlew test
cd ..
```

Expected: PASS (implementation already exists from Task 2)

**Step 3: Commit**

```bash
git add backend/src/test/kotlin/no/shoppinglist/HealthRouteTest.kt
git commit -m "test: add health endpoint test"
```

---

## Task 5: Backend Database Configuration

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/config/TestDatabaseConfig.kt`

**Step 1: Create DatabaseConfig.kt**

Create `backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt`:

```kotlin
package no.shoppinglist.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

object DatabaseConfig {

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

        Database.connect(HikariDataSource(config))
    }
}
```

**Step 2: Create TestDatabaseConfig.kt**

Create `backend/src/test/kotlin/no/shoppinglist/config/TestDatabaseConfig.kt`:

```kotlin
package no.shoppinglist.config

import io.kotest.core.listeners.ProjectListener
import io.kotest.core.spec.Spec
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer

object TestDatabaseConfig {

    private val container = PostgreSQLContainer("postgres:16-alpine").apply {
        withDatabaseName("test_shopping_list")
        withUsername("test")
        withPassword("test")
    }

    fun init(): Database {
        if (!container.isRunning) {
            container.start()
        }

        return Database.connect(
            url = container.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = container.username,
            password = container.password
        )
    }

    fun stop() {
        if (container.isRunning) {
            container.stop()
        }
    }
}
```

**Step 3: Verify compilation**

```bash
cd backend
./gradlew compileKotlin compileTestKotlin
cd ..
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/config/DatabaseConfig.kt
git add backend/src/test/kotlin/no/shoppinglist/config/TestDatabaseConfig.kt
git commit -m "feat: add database configuration with Testcontainers"
```

---

## Task 6: Backend Account Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/Account.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/AccountTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/AccountTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class AccountTest : FunSpec({

    beforeSpec {
        TestDatabaseConfig.init()
        transaction {
            SchemaUtils.create(Accounts)
        }
    }

    afterSpec {
        transaction {
            SchemaUtils.drop(Accounts)
        }
    }

    test("can create and retrieve account") {
        val accountId = UUID.randomUUID()
        val email = "test@example.com"
        val displayName = "Test User"

        transaction {
            Account.new(accountId) {
                this.email = email
                this.displayName = displayName
                this.avatarUrl = null
                this.createdAt = Instant.now()
            }
        }

        val retrieved = transaction {
            Account.findById(accountId)
        }

        retrieved.shouldNotBeNull()
        retrieved.email shouldBe email
        retrieved.displayName shouldBe displayName
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.AccountTest"
cd ..
```

Expected: FAIL with "Unresolved reference: Accounts"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/Account.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object Accounts : UUIDTable("accounts") {
    val email = varchar("email", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val createdAt = timestamp("created_at")
}

class Account(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(Accounts)

    var email by Accounts.email
    var displayName by Accounts.displayName
    var avatarUrl by Accounts.avatarUrl
    var createdAt by Accounts.createdAt
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.AccountTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/Account.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/AccountTest.kt
git commit -m "feat: add Account entity with tests"
```

---

## Task 7: Backend Household Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/Household.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/HouseholdTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/HouseholdTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class HouseholdTest : FunSpec({

    beforeSpec {
        TestDatabaseConfig.init()
        transaction {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships)
        }
    }

    afterSpec {
        transaction {
            SchemaUtils.drop(HouseholdMemberships, Households, Accounts)
        }
    }

    test("can create household with owner") {
        val accountId = UUID.randomUUID()
        val householdId = UUID.randomUUID()

        transaction {
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

        val household = transaction { Household.findById(householdId) }
        household.shouldNotBeNull()
        household.name shouldBe "Test Household"

        val memberships = transaction {
            HouseholdMembership.find { HouseholdMemberships.household eq householdId }.toList()
        }
        memberships shouldHaveSize 1
        memberships[0].role shouldBe MembershipRole.OWNER
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.HouseholdTest"
cd ..
```

Expected: FAIL with "Unresolved reference: Households"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/Household.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object Households : UUIDTable("households") {
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at")
}

class Household(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Household>(Households)

    var name by Households.name
    var createdAt by Households.createdAt
}

enum class MembershipRole {
    OWNER,
    MEMBER
}

object HouseholdMemberships : UUIDTable("household_memberships") {
    val account = reference("account_id", Accounts)
    val household = reference("household_id", Households)
    val role = enumerationByName<MembershipRole>("role", 20)
    val joinedAt = timestamp("joined_at")

    init {
        uniqueIndex(account, household)
    }
}

class HouseholdMembership(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<HouseholdMembership>(HouseholdMemberships)

    var account by Account referencedOn HouseholdMemberships.account
    var household by Household referencedOn HouseholdMemberships.household
    var role by HouseholdMemberships.role
    var joinedAt by HouseholdMemberships.joinedAt
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.HouseholdTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/Household.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/HouseholdTest.kt
git commit -m "feat: add Household and HouseholdMembership entities"
```

---

## Task 8: Backend ShoppingList Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/ShoppingList.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/ShoppingListTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/ShoppingListTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ShoppingListTest : FunSpec({

    lateinit var accountId: UUID
    lateinit var householdId: UUID

    beforeSpec {
        TestDatabaseConfig.init()
        accountId = UUID.randomUUID()
        householdId = UUID.randomUUID()

        transaction {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists)

            Account.new(accountId) {
                email = "test@example.com"
                displayName = "Test User"
                avatarUrl = null
                createdAt = Instant.now()
            }

            Household.new(householdId) {
                name = "Test Household"
                createdAt = Instant.now()
            }
        }
    }

    afterSpec {
        transaction {
            SchemaUtils.drop(ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("can create shopping list in household") {
        val listId = UUID.randomUUID()

        transaction {
            ShoppingList.new(listId) {
                name = "Groceries"
                owner = Account[accountId]
                household = Household[householdId]
                isPersonal = false
                createdAt = Instant.now()
            }
        }

        val list = transaction { ShoppingList.findById(listId) }
        list.shouldNotBeNull()
        list.name shouldBe "Groceries"
        list.isPersonal.shouldBeFalse()
    }

    test("can create personal list without household") {
        val listId = UUID.randomUUID()

        transaction {
            ShoppingList.new(listId) {
                name = "Personal List"
                owner = Account[accountId]
                household = null
                isPersonal = true
                createdAt = Instant.now()
            }
        }

        val list = transaction { ShoppingList.findById(listId) }
        list.shouldNotBeNull()
        list.household.shouldBeNull()
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ShoppingListTest"
cd ..
```

Expected: FAIL with "Unresolved reference: ShoppingLists"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/ShoppingList.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object ShoppingLists : UUIDTable("shopping_lists") {
    val name = varchar("name", 255)
    val owner = reference("owner_id", Accounts)
    val household = reference("household_id", Households).nullable()
    val isPersonal = bool("is_personal").default(false)
    val createdAt = timestamp("created_at")
}

class ShoppingList(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ShoppingList>(ShoppingLists)

    var name by ShoppingLists.name
    var owner by Account referencedOn ShoppingLists.owner
    var household by Household optionalReferencedOn ShoppingLists.household
    var isPersonal by ShoppingLists.isPersonal
    var createdAt by ShoppingLists.createdAt
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ShoppingListTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/ShoppingList.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/ShoppingListTest.kt
git commit -m "feat: add ShoppingList entity"
```

---

## Task 9: Backend ListItem Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/ListItem.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/ListItemTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/ListItemTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ListItemTest : FunSpec({

    lateinit var accountId: UUID
    lateinit var listId: UUID

    beforeSpec {
        TestDatabaseConfig.init()
        accountId = UUID.randomUUID()
        listId = UUID.randomUUID()

        transaction {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems)

            Account.new(accountId) {
                email = "test@example.com"
                displayName = "Test User"
                avatarUrl = null
                createdAt = Instant.now()
            }

            ShoppingList.new(listId) {
                name = "Test List"
                owner = Account[accountId]
                household = null
                isPersonal = true
                createdAt = Instant.now()
            }
        }
    }

    afterSpec {
        transaction {
            SchemaUtils.drop(ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("can create list item with quantity") {
        val itemId = UUID.randomUUID()

        transaction {
            ListItem.new(itemId) {
                list = ShoppingList[listId]
                name = "Milk"
                quantity = 2.0
                unit = "liters"
                barcode = null
                isChecked = false
                checkedBy = null
                createdBy = Account[accountId]
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        }

        val item = transaction { ListItem.findById(itemId) }
        item.shouldNotBeNull()
        item.name shouldBe "Milk"
        item.quantity shouldBe 2.0
        item.unit shouldBe "liters"
        item.isChecked.shouldBeFalse()
    }

    test("can check item") {
        val itemId = UUID.randomUUID()

        transaction {
            ListItem.new(itemId) {
                list = ShoppingList[listId]
                name = "Bread"
                quantity = 1.0
                unit = null
                barcode = null
                isChecked = false
                checkedBy = null
                createdBy = Account[accountId]
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        }

        transaction {
            val item = ListItem[itemId]
            item.isChecked = true
            item.checkedBy = Account[accountId]
            item.updatedAt = Instant.now()
        }

        val item = transaction { ListItem.findById(itemId) }
        item.shouldNotBeNull()
        item.isChecked.shouldBeTrue()
        item.checkedBy.shouldNotBeNull()
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ListItemTest"
cd ..
```

Expected: FAIL with "Unresolved reference: ListItems"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/ListItem.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object ListItems : UUIDTable("list_items") {
    val list = reference("list_id", ShoppingLists)
    val name = varchar("name", 255)
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val barcode = varchar("barcode", 100).nullable()
    val isChecked = bool("is_checked").default(false)
    val checkedBy = reference("checked_by_id", Accounts).nullable()
    val createdBy = reference("created_by_id", Accounts)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

class ListItem(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListItem>(ListItems)

    var list by ShoppingList referencedOn ListItems.list
    var name by ListItems.name
    var quantity by ListItems.quantity
    var unit by ListItems.unit
    var barcode by ListItems.barcode
    var isChecked by ListItems.isChecked
    var checkedBy by Account optionalReferencedOn ListItems.checkedBy
    var createdBy by Account referencedOn ListItems.createdBy
    var createdAt by ListItems.createdAt
    var updatedAt by ListItems.updatedAt
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ListItemTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/ListItem.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/ListItemTest.kt
git commit -m "feat: add ListItem entity"
```

---

## Task 10: Backend ListShare Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/ListShare.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/ListShareTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/ListShareTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ListShareTest : FunSpec({

    lateinit var ownerAccountId: UUID
    lateinit var sharedAccountId: UUID
    lateinit var listId: UUID

    beforeSpec {
        TestDatabaseConfig.init()
        ownerAccountId = UUID.randomUUID()
        sharedAccountId = UUID.randomUUID()
        listId = UUID.randomUUID()

        transaction {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)

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
        transaction {
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("can create user share with write permission") {
        val shareId = UUID.randomUUID()

        transaction {
            ListShare.new(shareId) {
                list = ShoppingList[listId]
                type = ShareType.USER
                account = Account[sharedAccountId]
                linkToken = null
                permission = SharePermission.WRITE
                createdAt = Instant.now()
            }
        }

        val share = transaction { ListShare.findById(shareId) }
        share.shouldNotBeNull()
        share.type shouldBe ShareType.USER
        share.permission shouldBe SharePermission.WRITE
        share.account.shouldNotBeNull()
        share.linkToken.shouldBeNull()
    }

    test("can create link share with read permission") {
        val shareId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()

        transaction {
            ListShare.new(shareId) {
                list = ShoppingList[listId]
                type = ShareType.LINK
                account = null
                linkToken = token
                permission = SharePermission.READ
                createdAt = Instant.now()
            }
        }

        val share = transaction { ListShare.findById(shareId) }
        share.shouldNotBeNull()
        share.type shouldBe ShareType.LINK
        share.permission shouldBe SharePermission.READ
        share.account.shouldBeNull()
        share.linkToken shouldBe token
    }

    test("can create link share with check permission") {
        val shareId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()

        transaction {
            ListShare.new(shareId) {
                list = ShoppingList[listId]
                type = ShareType.LINK
                account = null
                linkToken = token
                permission = SharePermission.CHECK
                createdAt = Instant.now()
            }
        }

        val share = transaction { ListShare.findById(shareId) }
        share.shouldNotBeNull()
        share.permission shouldBe SharePermission.CHECK
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ListShareTest"
cd ..
```

Expected: FAIL with "Unresolved reference: ListShares"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/ListShare.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

enum class ShareType {
    USER,
    LINK
}

enum class SharePermission {
    READ,
    CHECK,
    WRITE
}

object ListShares : UUIDTable("list_shares") {
    val list = reference("list_id", ShoppingLists)
    val type = enumerationByName<ShareType>("type", 20)
    val account = reference("account_id", Accounts).nullable()
    val linkToken = varchar("link_token", 255).nullable()
    val permission = enumerationByName<SharePermission>("permission", 20)
    val createdAt = timestamp("created_at")
}

class ListShare(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ListShare>(ListShares)

    var list by ShoppingList referencedOn ListShares.list
    var type by ListShares.type
    var account by Account optionalReferencedOn ListShares.account
    var linkToken by ListShares.linkToken
    var permission by ListShares.permission
    var createdAt by ListShares.createdAt
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.ListShareTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/ListShare.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/ListShareTest.kt
git commit -m "feat: add ListShare entity with permission levels"
```

---

## Task 11: Backend RecurringItem Entity

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/domain/RecurringItem.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/domain/RecurringItemTest.kt`

**Step 1: Write the failing test**

Create `backend/src/test/kotlin/no/shoppinglist/domain/RecurringItemTest.kt`:

```kotlin
package no.shoppinglist.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.shoppinglist.config.TestDatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.*

class RecurringItemTest : FunSpec({

    lateinit var accountId: UUID
    lateinit var listId: UUID

    beforeSpec {
        TestDatabaseConfig.init()
        accountId = UUID.randomUUID()
        listId = UUID.randomUUID()

        transaction {
            SchemaUtils.create(
                Accounts, Households, HouseholdMemberships,
                ShoppingLists, ListItems, ListShares, RecurringItems
            )

            Account.new(accountId) {
                email = "test@example.com"
                displayName = "Test User"
                avatarUrl = null
                createdAt = Instant.now()
            }

            ShoppingList.new(listId) {
                name = "Weekly Groceries"
                owner = Account[accountId]
                household = null
                isPersonal = true
                createdAt = Instant.now()
            }
        }
    }

    afterSpec {
        transaction {
            SchemaUtils.drop(
                RecurringItems, ListShares, ListItems,
                ShoppingLists, HouseholdMemberships, Households, Accounts
            )
        }
    }

    test("can create weekly recurring item") {
        val recurringId = UUID.randomUUID()
        val nextOccurrence = LocalDate.now().plusDays(7)

        transaction {
            RecurringItem.new(recurringId) {
                list = ShoppingList[listId]
                name = "Milk"
                quantity = 2.0
                unit = "liters"
                frequency = RecurringFrequency.WEEKLY
                this.nextOccurrence = nextOccurrence
                isActive = true
                createdBy = Account[accountId]
            }
        }

        val recurring = transaction { RecurringItem.findById(recurringId) }
        recurring.shouldNotBeNull()
        recurring.name shouldBe "Milk"
        recurring.frequency shouldBe RecurringFrequency.WEEKLY
        recurring.nextOccurrence shouldBe nextOccurrence
        recurring.isActive.shouldBeTrue()
    }

    test("can pause recurring item") {
        val recurringId = UUID.randomUUID()

        transaction {
            RecurringItem.new(recurringId) {
                list = ShoppingList[listId]
                name = "Bread"
                quantity = 1.0
                unit = null
                frequency = RecurringFrequency.DAILY
                nextOccurrence = LocalDate.now().plusDays(1)
                isActive = true
                createdBy = Account[accountId]
            }
        }

        transaction {
            val recurring = RecurringItem[recurringId]
            recurring.isActive = false
        }

        val recurring = transaction { RecurringItem.findById(recurringId) }
        recurring.shouldNotBeNull()
        recurring.isActive.shouldBeFalse()
    }
})
```

**Step 2: Run test to verify it fails**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.RecurringItemTest"
cd ..
```

Expected: FAIL with "Unresolved reference: RecurringItems"

**Step 3: Write minimal implementation**

Create `backend/src/main/kotlin/no/shoppinglist/domain/RecurringItem.kt`:

```kotlin
package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.date
import java.util.*

enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}

object RecurringItems : UUIDTable("recurring_items") {
    val list = reference("list_id", ShoppingLists)
    val name = varchar("name", 255)
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val frequency = enumerationByName<RecurringFrequency>("frequency", 20)
    val nextOccurrence = date("next_occurrence")
    val isActive = bool("is_active").default(true)
    val createdBy = reference("created_by_id", Accounts)
}

class RecurringItem(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RecurringItem>(RecurringItems)

    var list by ShoppingList referencedOn RecurringItems.list
    var name by RecurringItems.name
    var quantity by RecurringItems.quantity
    var unit by RecurringItems.unit
    var frequency by RecurringItems.frequency
    var nextOccurrence by RecurringItems.nextOccurrence
    var isActive by RecurringItems.isActive
    var createdBy by Account referencedOn RecurringItems.createdBy
}
```

**Step 4: Run test to verify it passes**

```bash
cd backend
./gradlew test --tests "no.shoppinglist.domain.RecurringItemTest"
cd ..
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/RecurringItem.kt
git add backend/src/test/kotlin/no/shoppinglist/domain/RecurringItemTest.kt
git commit -m "feat: add RecurringItem entity"
```

---

## Task 12: Backend Run All Tests

**Step 1: Run all backend tests**

```bash
cd backend
./gradlew test
cd ..
```

Expected: All tests PASS

**Step 2: Run linting**

```bash
cd backend
./gradlew detekt ktlintCheck
cd ..
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit if any lint fixes needed**

If ktlint reports issues:
```bash
cd backend
./gradlew ktlintFormat
cd ..
git add -A
git commit -m "style: apply ktlint formatting"
```

---

## Task 13: Web Project Setup (React + TypeScript)

**Files:**
- Create: `web/package.json`
- Create: `web/tsconfig.json`
- Create: `web/vite.config.ts`
- Create: `web/index.html`
- Create: `web/src/main.tsx`
- Create: `web/src/App.tsx`

**Step 1: Initialize React project with Vite**

```bash
cd web
npm create vite@latest . -- --template react-ts
cd ..
```

When prompted, select to overwrite existing files.

**Step 2: Install dependencies**

```bash
cd web
npm install
npm install @reduxjs/toolkit react-redux
npm install -D @testing-library/react @testing-library/jest-dom vitest jsdom @types/node
cd ..
```

**Step 3: Verify web compiles**

```bash
cd web
npm run build
cd ..
```

Expected: Build successful

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: add React/TypeScript web project"
```

---

## Task 14: Web ESLint and Prettier Configuration

**Files:**
- Create: `web/.eslintrc.cjs`
- Create: `web/.prettierrc`
- Modify: `web/package.json`

**Step 1: Install ESLint and Prettier**

```bash
cd web
npm install -D eslint @typescript-eslint/eslint-plugin @typescript-eslint/parser
npm install -D eslint-plugin-react eslint-plugin-react-hooks
npm install -D prettier eslint-config-prettier eslint-plugin-prettier
cd ..
```

**Step 2: Create .eslintrc.cjs**

Create `web/.eslintrc.cjs`:

```javascript
module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:@typescript-eslint/recommended-requiring-type-checking',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
    'plugin:prettier/recommended',
  ],
  ignorePatterns: ['dist', '.eslintrc.cjs', 'vite.config.ts'],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    project: ['./tsconfig.json', './tsconfig.node.json'],
    tsconfigRootDir: __dirname,
  },
  plugins: ['react', '@typescript-eslint', 'prettier'],
  settings: {
    react: {
      version: 'detect',
    },
  },
  rules: {
    'react/react-in-jsx-scope': 'off',
    'prettier/prettier': 'error',
  },
};
```

**Step 3: Create .prettierrc**

Create `web/.prettierrc`:

```json
{
  "semi": true,
  "trailingComma": "es5",
  "singleQuote": true,
  "printWidth": 100,
  "tabWidth": 2
}
```

**Step 4: Add lint scripts to package.json**

Update `web/package.json` scripts section:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
    "lint:fix": "eslint . --ext ts,tsx --fix",
    "format": "prettier --write \"src/**/*.{ts,tsx}\"",
    "preview": "vite preview",
    "test": "vitest",
    "test:run": "vitest run"
  }
}
```

**Step 5: Run lint and fix issues**

```bash
cd web
npm run lint:fix
npm run format
cd ..
```

**Step 6: Commit**

```bash
git add -A
git commit -m "feat: add ESLint and Prettier configuration"
```

---

## Task 15: Web Vitest Configuration

**Files:**
- Create: `web/vitest.config.ts`
- Create: `web/src/test/setup.ts`

**Step 1: Create vitest.config.ts**

Create `web/vitest.config.ts`:

```typescript
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
```

**Step 2: Create test setup file**

Create `web/src/test/setup.ts`:

```typescript
import '@testing-library/jest-dom';
```

**Step 3: Update tsconfig.json for vitest globals**

Add to `web/tsconfig.json` compilerOptions:

```json
{
  "compilerOptions": {
    "types": ["vitest/globals"]
  }
}
```

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: add Vitest test configuration"
```

---

## Task 16: Web App Component Test

**Files:**
- Create: `web/src/App.test.tsx`

**Step 1: Write the test**

Create `web/src/App.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import App from './App';

describe('App', () => {
  it('renders without crashing', () => {
    render(<App />);
    expect(document.body).toBeInTheDocument();
  });
});
```

**Step 2: Run test**

```bash
cd web
npm run test:run
cd ..
```

Expected: PASS

**Step 3: Commit**

```bash
git add web/src/App.test.tsx
git commit -m "test: add App component test"
```

---

## Task 17: Web Redux Store Setup

**Files:**
- Create: `web/src/store/index.ts`
- Create: `web/src/store/hooks.ts`
- Modify: `web/src/main.tsx`

**Step 1: Create Redux store**

Create `web/src/store/index.ts`:

```typescript
import { configureStore } from '@reduxjs/toolkit';

export const store = configureStore({
  reducer: {},
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

**Step 2: Create typed hooks**

Create `web/src/store/hooks.ts`:

```typescript
import { useDispatch, useSelector, TypedUseSelectorHook } from 'react-redux';
import type { RootState, AppDispatch } from './index';

export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
```

**Step 3: Update main.tsx with Provider**

Update `web/src/main.tsx`:

```typescript
import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { store } from './store';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </React.StrictMode>
);
```

**Step 4: Run tests and lint**

```bash
cd web
npm run test:run
npm run lint
cd ..
```

Expected: All pass

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: add Redux store setup"
```

---

## Task 18: Final Verification

**Step 1: Run all backend tests**

```bash
cd backend
./gradlew test
cd ..
```

Expected: All PASS

**Step 2: Run backend linting**

```bash
cd backend
./gradlew detekt ktlintCheck
cd ..
```

Expected: BUILD SUCCESSFUL

**Step 3: Run web tests**

```bash
cd web
npm run test:run
cd ..
```

Expected: All PASS

**Step 4: Run web linting**

```bash
cd web
npm run lint
cd ..
```

Expected: No errors

**Step 5: Start both services (manual verification)**

Terminal 1:
```bash
docker-compose up -d
cd backend
./gradlew run
```

Terminal 2:
```bash
cd web
npm run dev
```

Verify:
- Backend health: `curl http://localhost:8080/health` returns "OK"
- Web: http://localhost:5173 loads React app

**Step 6: Final commit**

```bash
git add -A
git commit -m "feat: complete Phase 1 foundation setup"
```

---

## Phase 1 Complete

At the end of Phase 1, you have:

**Backend:**
- Ktor project with Gradle
- PostgreSQL with Exposed ORM
- All domain entities: Account, Household, HouseholdMembership, ShoppingList, ListItem, ListShare, RecurringItem
- Detekt + ktlint configured
- Kotest unit tests with Testcontainers

**Web:**
- React 18 + TypeScript with Vite
- Redux Toolkit store
- ESLint + Prettier configured
- Vitest for testing

**Infrastructure:**
- Docker Compose for PostgreSQL
- Project README

**Next: Phase 2 - Authentication** (Google OAuth in backend + login UI in web)
