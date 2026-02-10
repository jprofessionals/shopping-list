# Phase 4: Shopping Lists Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement shopping lists with items and sharing (user + link-based with expiration).

**Architecture:** Backend services for ShoppingList, ListItem, and ListShare with permission-based access. Frontend Redux slices and components for list management, items, and sharing.

**Tech Stack:** Ktor, Exposed ORM, Kotest (backend); React, Redux Toolkit, Vitest (frontend)

---

## Task 1: Add expiresAt to ListShare Domain

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/domain/ListShare.kt`

**Step 1: Add expiresAt column to ListShares table**

```kotlin
object ListShares : UUIDTable("list_shares") {
    val list = reference("list_id", ShoppingLists)
    val type = enumerationByName<ShareType>("type", 20)
    val account = reference("account_id", Accounts).nullable()
    val linkToken = varchar("link_token", 255).nullable().uniqueIndex()
    val permission = enumerationByName<SharePermission>("permission", 20)
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at")
}
```

**Step 2: Add expiresAt property to ListShare entity**

Add after `createdAt`:
```kotlin
var expiresAt by ListShares.expiresAt
```

**Step 3: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/ListShare.kt
git commit -m "feat: add expiresAt to ListShare for link expiration"
```

---

## Task 2: Update ListItem Domain for TEXT Name

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/domain/ListItem.kt`

**Step 1: Change name column to text type**

```kotlin
object ListItems : UUIDTable("list_items") {
    val list = reference("list_id", ShoppingLists)
    val name = text("name")
    val quantity = double("quantity")
    val unit = varchar("unit", 50).nullable()
    val barcode = varchar("barcode", 100).nullable()
    val isChecked = bool("is_checked").default(false)
    val checkedBy = reference("checked_by_id", Accounts).nullable()
    val createdBy = reference("created_by_id", Accounts)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
```

**Step 2: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/domain/ListItem.kt
git commit -m "feat: change ListItem name to TEXT for longer item names"
```

---

## Task 3: ShoppingListService - Create and FindById

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt`

**Step 1: Write failing tests**

```kotlin
package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListServiceTest : FunSpec({
    lateinit var db: Database
    lateinit var service: ShoppingListService
    lateinit var testAccountId: UUID

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
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
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
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
})
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: FAIL (class not found)

**Step 3: Implement ShoppingListService**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListService(private val db: Database) {

    fun create(name: String, ownerId: UUID, householdId: UUID?, isPersonal: Boolean): ShoppingList =
        transaction(db) {
            val owner = Account.findById(ownerId)
                ?: throw IllegalArgumentException("Account not found: $ownerId")
            val household = householdId?.let {
                Household.findById(it) ?: throw IllegalArgumentException("Household not found: $it")
            }

            ShoppingList.new {
                this.name = name
                this.owner = owner
                this.household = household
                this.isPersonal = isPersonal
                this.createdAt = Instant.now()
            }
        }

    fun findById(id: UUID): ShoppingList? = transaction(db) {
        ShoppingList.findById(id)
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt \
        backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt
git commit -m "feat: add ShoppingListService with create and findById"
```

---

## Task 4: ShoppingListService - Access Resolution

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt`
- Modify: `backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt`

**Step 1: Add access resolution tests**

Add to test file:
```kotlin
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
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: FAIL

**Step 3: Implement getPermission**

Add to ShoppingListService:
```kotlin
    fun getPermission(listId: UUID, accountId: UUID?, linkToken: String?): SharePermission? =
        transaction(db) {
            val list = ShoppingList.findById(listId) ?: return@transaction null

            // 1. Owner has full access
            if (accountId != null && list.owner.id.value == accountId) {
                return@transaction SharePermission.WRITE
            }

            // 2. Household member (non-personal) has full access
            if (accountId != null && list.household != null && !list.isPersonal) {
                val isMember = HouseholdMembership
                    .find {
                        (HouseholdMemberships.household eq list.household!!.id) and
                            (HouseholdMemberships.account eq accountId)
                    }.firstOrNull() != null
                if (isMember) {
                    return@transaction SharePermission.WRITE
                }
            }

            // 3. User share
            if (accountId != null) {
                val userShare = ListShare
                    .find {
                        (ListShares.list eq listId) and
                            (ListShares.type eq ShareType.USER) and
                            (ListShares.account eq accountId)
                    }.firstOrNull()
                if (userShare != null) {
                    return@transaction userShare.permission
                }
            }

            // 4. Link share (valid token, not expired)
            if (linkToken != null) {
                val linkShare = ListShare
                    .find {
                        (ListShares.list eq listId) and
                            (ListShares.type eq ShareType.LINK) and
                            (ListShares.linkToken eq linkToken)
                    }.firstOrNull()
                if (linkShare != null) {
                    val now = Instant.now()
                    if (linkShare.expiresAt == null || linkShare.expiresAt!! > now) {
                        return@transaction linkShare.permission
                    }
                }
            }

            null
        }
```

Add imports:
```kotlin
import org.jetbrains.exposed.sql.and
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt \
        backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt
git commit -m "feat: add permission resolution to ShoppingListService"
```

---

## Task 5: ShoppingListService - Update, Delete, FindAccessible

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt`
- Modify: `backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt`

**Step 1: Add tests**

```kotlin
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
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: FAIL

**Step 3: Implement methods**

Add to ShoppingListService:
```kotlin
    fun update(id: UUID, name: String, isPersonal: Boolean): ShoppingList? =
        transaction(db) {
            val list = ShoppingList.findById(id) ?: return@transaction null
            list.name = name
            list.isPersonal = isPersonal
            list
        }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            ListShares.deleteWhere { list eq id }
            ListItems.deleteWhere { ListItems.list eq id }
            val deleted = ShoppingLists.deleteWhere { ShoppingLists.id eq id }
            deleted > 0
        }

    fun findAccessibleByAccount(accountId: UUID): List<ShoppingList> =
        transaction(db) {
            val owned = ShoppingList.find { ShoppingLists.owner eq accountId }.toList()

            val householdIds = HouseholdMembership
                .find { HouseholdMemberships.account eq accountId }
                .map { it.household.id.value }

            val householdLists = if (householdIds.isNotEmpty()) {
                ShoppingList.find {
                    (ShoppingLists.household inList householdIds) and
                        (ShoppingLists.isPersonal eq false) and
                        (ShoppingLists.owner neq accountId)
                }.toList()
            } else {
                emptyList()
            }

            val sharedLists = ListShare
                .find {
                    (ListShares.type eq ShareType.USER) and
                        (ListShares.account eq accountId)
                }.map { it.list }

            (owned + householdLists + sharedLists).distinctBy { it.id.value }
        }
```

Add imports:
```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.deleteWhere
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ShoppingListServiceTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/ShoppingListService.kt \
        backend/src/test/kotlin/no/shoppinglist/service/ShoppingListServiceTest.kt
git commit -m "feat: add update, delete, findAccessibleByAccount to ShoppingListService"
```

---

## Task 6: ListItemService - CRUD Operations

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/ListItemService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/ListItemServiceTest.kt`

**Step 1: Write failing tests**

```kotlin
package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListItemServiceTest : FunSpec({
    lateinit var db: Database
    lateinit var service: ListItemService
    lateinit var listService: ShoppingListService
    lateinit var testAccountId: UUID
    lateinit var testListId: UUID

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
        }
        service = ListItemService(db)
        listService = ShoppingListService(db)
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
        val list = listService.create("Test List", testAccountId, null, false)
        testListId = list.id.value
    }

    afterSpec {
        transaction(db) {
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("create adds item to list") {
        val item = service.create(testListId, "Milk", 2.0, "liters", null, testAccountId)

        item shouldNotBe null
        transaction(db) {
            item.name shouldBe "Milk"
            item.quantity shouldBe 2.0
            item.unit shouldBe "liters"
            item.isChecked shouldBe false
        }
    }

    test("findByListId returns all items") {
        service.create(testListId, "Milk", 1.0, null, null, testAccountId)
        service.create(testListId, "Bread", 2.0, null, null, testAccountId)

        val items = service.findByListId(testListId)

        items shouldHaveSize 2
    }

    test("update changes item properties") {
        val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

        val updated = service.update(item.id.value, "Whole Milk", 2.0, "liters")

        updated shouldNotBe null
        transaction(db) {
            updated!!.name shouldBe "Whole Milk"
            updated.quantity shouldBe 2.0
            updated.unit shouldBe "liters"
        }
    }

    test("toggleCheck changes isChecked and sets checkedBy") {
        val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

        val toggled = service.toggleCheck(item.id.value, testAccountId)

        toggled shouldNotBe null
        transaction(db) {
            toggled!!.isChecked shouldBe true
            toggled.checkedBy?.id?.value shouldBe testAccountId
        }
    }

    test("toggleCheck unchecks and clears checkedBy") {
        val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)
        service.toggleCheck(item.id.value, testAccountId)

        val toggled = service.toggleCheck(item.id.value, testAccountId)

        toggled shouldNotBe null
        transaction(db) {
            toggled!!.isChecked shouldBe false
            toggled.checkedBy shouldBe null
        }
    }

    test("delete removes item") {
        val item = service.create(testListId, "Milk", 1.0, null, null, testAccountId)

        val result = service.delete(item.id.value)

        result shouldBe true
        service.findByListId(testListId) shouldHaveSize 0
    }
})
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ListItemServiceTest" -q`
Expected: FAIL

**Step 3: Implement ListItemService**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ListItemService(private val db: Database) {

    fun create(
        listId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
        barcode: String?,
        createdById: UUID,
    ): ListItem = transaction(db) {
        val list = ShoppingList.findById(listId)
            ?: throw IllegalArgumentException("List not found: $listId")
        val createdBy = Account.findById(createdById)
            ?: throw IllegalArgumentException("Account not found: $createdById")
        val now = Instant.now()

        ListItem.new {
            this.list = list
            this.name = name
            this.quantity = quantity
            this.unit = unit
            this.barcode = barcode
            this.isChecked = false
            this.checkedBy = null
            this.createdBy = createdBy
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findByListId(listId: UUID): List<ListItem> = transaction(db) {
        ListItem.find { ListItems.list eq listId }.toList()
    }

    fun findById(id: UUID): ListItem? = transaction(db) {
        ListItem.findById(id)
    }

    fun update(id: UUID, name: String, quantity: Double, unit: String?): ListItem? =
        transaction(db) {
            val item = ListItem.findById(id) ?: return@transaction null
            item.name = name
            item.quantity = quantity
            item.unit = unit
            item.updatedAt = Instant.now()
            item
        }

    fun toggleCheck(id: UUID, accountId: UUID): ListItem? = transaction(db) {
        val item = ListItem.findById(id) ?: return@transaction null
        val account = Account.findById(accountId)

        if (item.isChecked) {
            item.isChecked = false
            item.checkedBy = null
        } else {
            item.isChecked = true
            item.checkedBy = account
        }
        item.updatedAt = Instant.now()
        item
    }

    fun delete(id: UUID): Boolean = transaction(db) {
        val deleted = ListItems.deleteWhere { ListItems.id eq id }
        deleted > 0
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ListItemServiceTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/ListItemService.kt \
        backend/src/test/kotlin/no/shoppinglist/service/ListItemServiceTest.kt
git commit -m "feat: add ListItemService with full CRUD and check toggle"
```

---

## Task 7: ListShareService - Create and Revoke Shares

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/service/ListShareService.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/service/ListShareServiceTest.kt`

**Step 1: Write failing tests**

```kotlin
package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ListShareServiceTest : FunSpec({
    lateinit var db: Database
    lateinit var service: ListShareService
    lateinit var listService: ShoppingListService
    lateinit var testAccountId: UUID
    lateinit var otherAccountId: UUID
    lateinit var testListId: UUID

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
        }
        service = ListShareService(db)
        listService = ShoppingListService(db)
    }

    beforeTest {
        testAccountId = UUID.randomUUID()
        otherAccountId = UUID.randomUUID()
        transaction(db) {
            Account.new(testAccountId) {
                email = "test-${UUID.randomUUID()}@example.com"
                displayName = "Test User"
                createdAt = Instant.now()
            }
            Account.new(otherAccountId) {
                email = "other-${UUID.randomUUID()}@example.com"
                displayName = "Other User"
                createdAt = Instant.now()
            }
        }
        val list = listService.create("Test List", testAccountId, null, false)
        testListId = list.id.value
    }

    afterSpec {
        transaction(db) {
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("createUserShare creates share with account") {
        val share = service.createUserShare(testListId, otherAccountId, SharePermission.READ)

        share shouldNotBe null
        transaction(db) {
            share.type shouldBe ShareType.USER
            share.account?.id?.value shouldBe otherAccountId
            share.permission shouldBe SharePermission.READ
            share.expiresAt shouldBe null
        }
    }

    test("createLinkShare creates share with token and expiration") {
        val share = service.createLinkShare(testListId, SharePermission.CHECK, 7)

        share shouldNotBe null
        transaction(db) {
            share.type shouldBe ShareType.LINK
            share.linkToken shouldNotBe null
            share.linkToken!!.length shouldBe 32
            share.permission shouldBe SharePermission.CHECK
            share.expiresAt shouldNotBe null
        }
    }

    test("findByListId returns all shares") {
        service.createUserShare(testListId, otherAccountId, SharePermission.READ)
        service.createLinkShare(testListId, SharePermission.WRITE, 7)

        val shares = service.findByListId(testListId)

        shares shouldHaveSize 2
    }

    test("delete removes share") {
        val share = service.createUserShare(testListId, otherAccountId, SharePermission.READ)

        val result = service.delete(share.id.value)

        result shouldBe true
        service.findByListId(testListId) shouldHaveSize 0
    }

    test("findByToken returns valid link share") {
        val share = service.createLinkShare(testListId, SharePermission.READ, 7)
        val token = transaction(db) { share.linkToken!! }

        val found = service.findByToken(token)

        found shouldNotBe null
    }

    test("findByToken returns null for expired link") {
        val share = service.createLinkShare(testListId, SharePermission.READ, -1)
        val token = transaction(db) { share.linkToken!! }

        val found = service.findByToken(token)

        found shouldBe null
    }
})
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ListShareServiceTest" -q`
Expected: FAIL

**Step 3: Implement ListShareService**

```kotlin
package no.shoppinglist.service

import no.shoppinglist.domain.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ListShareService(private val db: Database) {

    private val secureRandom = SecureRandom()
    private val tokenChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun createUserShare(listId: UUID, accountId: UUID, permission: SharePermission): ListShare =
        transaction(db) {
            val list = ShoppingList.findById(listId)
                ?: throw IllegalArgumentException("List not found: $listId")
            val account = Account.findById(accountId)
                ?: throw IllegalArgumentException("Account not found: $accountId")

            ListShare.new {
                this.list = list
                this.type = ShareType.USER
                this.account = account
                this.linkToken = null
                this.permission = permission
                this.expiresAt = null
                this.createdAt = Instant.now()
            }
        }

    fun createLinkShare(listId: UUID, permission: SharePermission, expirationDays: Int): ListShare =
        transaction(db) {
            val list = ShoppingList.findById(listId)
                ?: throw IllegalArgumentException("List not found: $listId")

            ListShare.new {
                this.list = list
                this.type = ShareType.LINK
                this.account = null
                this.linkToken = generateToken()
                this.permission = permission
                this.expiresAt = Instant.now().plus(expirationDays.toLong(), ChronoUnit.DAYS)
                this.createdAt = Instant.now()
            }
        }

    fun findByListId(listId: UUID): List<ListShare> = transaction(db) {
        ListShare.find { ListShares.list eq listId }.toList()
    }

    fun findById(id: UUID): ListShare? = transaction(db) {
        ListShare.findById(id)
    }

    fun findByToken(token: String): ListShare? = transaction(db) {
        val share = ListShare
            .find { (ListShares.linkToken eq token) and (ListShares.type eq ShareType.LINK) }
            .firstOrNull() ?: return@transaction null

        if (share.expiresAt != null && share.expiresAt!! <= Instant.now()) {
            return@transaction null
        }
        share
    }

    fun update(id: UUID, permission: SharePermission, expiresAt: Instant?): ListShare? =
        transaction(db) {
            val share = ListShare.findById(id) ?: return@transaction null
            share.permission = permission
            if (share.type == ShareType.LINK) {
                share.expiresAt = expiresAt
            }
            share
        }

    fun delete(id: UUID): Boolean = transaction(db) {
        val deleted = ListShares.deleteWhere { ListShares.id eq id }
        deleted > 0
    }

    private fun generateToken(): String {
        val sb = StringBuilder(32)
        repeat(32) {
            sb.append(tokenChars[secureRandom.nextInt(tokenChars.length)])
        }
        return sb.toString()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.service.ListShareServiceTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/service/ListShareService.kt \
        backend/src/test/kotlin/no/shoppinglist/service/ListShareServiceTest.kt
git commit -m "feat: add ListShareService with user and link shares"
```

---

## Task 8: Shopping List Routes - CRUD Endpoints

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt`

**Step 1: Write failing tests**

```kotlin
package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import no.shoppinglist.service.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListRoutesTest : FunSpec({
    lateinit var db: Database
    lateinit var listService: ShoppingListService
    lateinit var itemService: ListItemService
    lateinit var shareService: ListShareService
    lateinit var householdService: HouseholdService
    lateinit var testAccountId: UUID

    fun Application.testModule() {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(null)
                validate { JWTPrincipal(it.payload) }
                skipWhen { true }
            }
        }
        authentication {
            provider("auth-jwt") {
                authenticate { context ->
                    JWTPrincipal(
                        com.auth0.jwt.JWT.create()
                            .withSubject(testAccountId.toString())
                            .sign(com.auth0.jwt.algorithms.Algorithm.none()),
                    )
                }
            }
        }
        routing {
            shoppingListRoutes(listService, itemService, shareService, householdService)
        }
    }

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
        }
        listService = ShoppingListService(db)
        itemService = ListItemService(db)
        shareService = ListShareService(db)
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
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("GET /lists returns empty list initially") {
        testApplication {
            application { testModule() }

            val response = client.get("/lists")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("POST /lists creates a new list") {
        testApplication {
            application { testModule() }

            val response = client.post("/lists") {
                contentType(ContentType.Application.Json)
                setBody("""{"name": "Groceries"}""")
            }

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText().contains("Groceries") shouldBe true
        }
    }

    test("GET /lists/:id returns list with items") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)

            val response = client.get("/lists/${list.id.value}")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().contains("Test List") shouldBe true
        }
    }

    test("DELETE /lists/:id requires owner") {
        testApplication {
            application { testModule() }
            val otherAccountId = UUID.randomUUID()
            transaction(db) {
                Account.new(otherAccountId) {
                    email = "other@example.com"
                    displayName = "Other"
                    createdAt = Instant.now()
                }
            }
            val list = listService.create("Other's List", otherAccountId, null, false)

            val response = client.delete("/lists/${list.id.value}")

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }
})
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: FAIL

**Step 3: Implement routes (create file with basic CRUD)**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.service.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateListRequest(val name: String, val householdId: String? = null, val isPersonal: Boolean = false)

@Serializable
data class UpdateListRequest(val name: String, val isPersonal: Boolean)

@Serializable
data class ListResponse(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
    val createdAt: String,
    val isOwner: Boolean,
)

@Serializable
data class ListDetailResponse(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
    val createdAt: String,
    val isOwner: Boolean,
    val items: List<ItemResponse>,
)

@Serializable
data class ItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val checkedByName: String?,
    val createdAt: String,
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.shoppingListRoutes(
    listService: ShoppingListService,
    itemService: ListItemService,
    shareService: ListShareService,
    householdService: HouseholdService,
) {
    authenticate("auth-jwt") {
        route("/lists") {
            get {
                val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val lists = listService.findAccessibleByAccount(accountId)
                val response = lists.map { list ->
                    transaction {
                        ListResponse(
                            id = list.id.value.toString(),
                            name = list.name,
                            householdId = list.household?.id?.value?.toString(),
                            isPersonal = list.isPersonal,
                            createdAt = list.createdAt.toString(),
                            isOwner = list.owner.id.value == accountId,
                        )
                    }
                }
                call.respond(response)
            }

            post {
                val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<CreateListRequest>()
                if (request.name.isBlank() || request.name.length > 255) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Name must be 1-255 chars"))
                }

                val householdId = request.householdId?.let { UUID.fromString(it) }
                if (householdId != null && !householdService.isMember(householdId, accountId)) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }

                val list = listService.create(request.name, accountId, householdId, request.isPersonal)
                call.respond(HttpStatusCode.Created, transaction {
                    ListResponse(
                        id = list.id.value.toString(),
                        name = list.name,
                        householdId = list.household?.id?.value?.toString(),
                        isPersonal = list.isPersonal,
                        createdAt = list.createdAt.toString(),
                        isOwner = true,
                    )
                })
            }

            route("/{id}") {
                get {
                    val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                    val permission = listService.getPermission(listId, accountId, null)
                        ?: return@get call.respond(HttpStatusCode.Forbidden)

                    val list = listService.findById(listId)
                        ?: return@get call.respond(HttpStatusCode.NotFound)

                    val items = itemService.findByListId(listId)

                    call.respond(transaction {
                        ListDetailResponse(
                            id = list.id.value.toString(),
                            name = list.name,
                            householdId = list.household?.id?.value?.toString(),
                            isPersonal = list.isPersonal,
                            createdAt = list.createdAt.toString(),
                            isOwner = list.owner.id.value == accountId,
                            items = items.map { item ->
                                ItemResponse(
                                    id = item.id.value.toString(),
                                    name = item.name,
                                    quantity = item.quantity,
                                    unit = item.unit,
                                    isChecked = item.isChecked,
                                    checkedByName = item.checkedBy?.displayName,
                                    createdAt = item.createdAt.toString(),
                                )
                            },
                        )
                    })
                }

                patch {
                    val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                    val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@patch call.respond(HttpStatusCode.BadRequest)

                    val permission = listService.getPermission(listId, accountId, null)
                    if (permission != SharePermission.WRITE) {
                        return@patch call.respond(HttpStatusCode.Forbidden)
                    }

                    val request = call.receive<UpdateListRequest>()
                    val updated = listService.update(listId, request.name, request.isPersonal)
                        ?: return@patch call.respond(HttpStatusCode.NotFound)

                    call.respond(transaction {
                        ListResponse(
                            id = updated.id.value.toString(),
                            name = updated.name,
                            householdId = updated.household?.id?.value?.toString(),
                            isPersonal = updated.isPersonal,
                            createdAt = updated.createdAt.toString(),
                            isOwner = updated.owner.id.value == accountId,
                        )
                    })
                }

                delete {
                    val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                    val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    val list = listService.findById(listId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)

                    val isOwner = transaction { list.owner.id.value == accountId }
                    if (!isOwner) {
                        return@delete call.respond(HttpStatusCode.Forbidden)
                    }

                    listService.delete(listId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt \
        backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt
git commit -m "feat: add shopping list CRUD routes"
```

---

## Task 9: Item Routes - CRUD and Check Toggle

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`
- Modify: `backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt`

**Step 1: Add item route tests**

Add to test file:
```kotlin
    test("POST /lists/:id/items adds item") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)

            val response = client.post("/lists/${list.id.value}/items") {
                contentType(ContentType.Application.Json)
                setBody("""{"name": "Milk", "quantity": 2}""")
            }

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText().contains("Milk") shouldBe true
        }
    }

    test("POST /lists/:id/items/:itemId/check toggles check") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)
            val item = itemService.create(list.id.value, "Milk", 1.0, null, null, testAccountId)

            val response = client.post("/lists/${list.id.value}/items/${item.id.value}/check")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().contains("true") shouldBe true
        }
    }

    test("DELETE /lists/:id/items/:itemId removes item") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)
            val item = itemService.create(list.id.value, "Milk", 1.0, null, null, testAccountId)

            val response = client.delete("/lists/${list.id.value}/items/${item.id.value}")

            response.status shouldBe HttpStatusCode.NoContent
        }
    }
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: FAIL

**Step 3: Add item routes to ShoppingListRoutes.kt**

Add request DTOs:
```kotlin
@Serializable
data class CreateItemRequest(val name: String, val quantity: Double = 1.0, val unit: String? = null)

@Serializable
data class UpdateItemRequest(val name: String, val quantity: Double, val unit: String?)
```

Add inside the `route("/{id}")` block, after delete:
```kotlin
                route("/items") {
                    post {
                        val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)

                        val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@post call.respond(HttpStatusCode.BadRequest)

                        val permission = listService.getPermission(listId, accountId, null)
                        if (permission != SharePermission.WRITE) {
                            return@post call.respond(HttpStatusCode.Forbidden)
                        }

                        val request = call.receive<CreateItemRequest>()
                        if (request.name.isBlank() || request.name.length > 1000) {
                            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Name must be 1-1000 chars"))
                        }

                        val item = itemService.create(listId, request.name, request.quantity, request.unit, null, accountId)
                        call.respond(HttpStatusCode.Created, transaction {
                            ItemResponse(
                                id = item.id.value.toString(),
                                name = item.name,
                                quantity = item.quantity,
                                unit = item.unit,
                                isChecked = item.isChecked,
                                checkedByName = null,
                                createdAt = item.createdAt.toString(),
                            )
                        })
                    }

                    route("/{itemId}") {
                        patch {
                            val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                                ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                            val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@patch call.respond(HttpStatusCode.BadRequest)

                            val itemId = call.parameters["itemId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@patch call.respond(HttpStatusCode.BadRequest)

                            val permission = listService.getPermission(listId, accountId, null)
                            if (permission != SharePermission.WRITE) {
                                return@patch call.respond(HttpStatusCode.Forbidden)
                            }

                            val request = call.receive<UpdateItemRequest>()
                            val updated = itemService.update(itemId, request.name, request.quantity, request.unit)
                                ?: return@patch call.respond(HttpStatusCode.NotFound)

                            call.respond(transaction {
                                ItemResponse(
                                    id = updated.id.value.toString(),
                                    name = updated.name,
                                    quantity = updated.quantity,
                                    unit = updated.unit,
                                    isChecked = updated.isChecked,
                                    checkedByName = updated.checkedBy?.displayName,
                                    createdAt = updated.createdAt.toString(),
                                )
                            })
                        }

                        delete {
                            val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                                ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                            val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@delete call.respond(HttpStatusCode.BadRequest)

                            val itemId = call.parameters["itemId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@delete call.respond(HttpStatusCode.BadRequest)

                            val permission = listService.getPermission(listId, accountId, null)
                            if (permission != SharePermission.WRITE) {
                                return@delete call.respond(HttpStatusCode.Forbidden)
                            }

                            itemService.delete(itemId)
                            call.respond(HttpStatusCode.NoContent)
                        }

                        post("/check") {
                            val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                                ?: return@post call.respond(HttpStatusCode.Unauthorized)

                            val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@post call.respond(HttpStatusCode.BadRequest)

                            val itemId = call.parameters["itemId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: return@post call.respond(HttpStatusCode.BadRequest)

                            val permission = listService.getPermission(listId, accountId, null)
                            if (permission == null || permission == SharePermission.READ) {
                                return@post call.respond(HttpStatusCode.Forbidden)
                            }

                            val toggled = itemService.toggleCheck(itemId, accountId)
                                ?: return@post call.respond(HttpStatusCode.NotFound)

                            call.respond(transaction {
                                ItemResponse(
                                    id = toggled.id.value.toString(),
                                    name = toggled.name,
                                    quantity = toggled.quantity,
                                    unit = toggled.unit,
                                    isChecked = toggled.isChecked,
                                    checkedByName = toggled.checkedBy?.displayName,
                                    createdAt = toggled.createdAt.toString(),
                                )
                            })
                        }
                    }
                }
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt \
        backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt
git commit -m "feat: add item CRUD and check toggle routes"
```

---

## Task 10: Share Routes and Public Access

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt`
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt`
- Modify: `backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt`

**Step 1: Add share route tests**

Add to ShoppingListRoutesTest:
```kotlin
    test("POST /lists/:id/shares creates user share") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)
            val otherAccount = transaction(db) {
                Account.new {
                    email = "share-target@example.com"
                    displayName = "Share Target"
                    createdAt = Instant.now()
                }
            }

            val response = client.post("/lists/${list.id.value}/shares") {
                contentType(ContentType.Application.Json)
                setBody("""{"type": "USER", "accountId": "${otherAccount.id.value}", "permission": "READ"}""")
            }

            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("POST /lists/:id/shares creates link share with expiration") {
        testApplication {
            application { testModule() }
            val list = listService.create("Test List", testAccountId, null, false)

            val response = client.post("/lists/${list.id.value}/shares") {
                contentType(ContentType.Application.Json)
                setBody("""{"type": "LINK", "permission": "CHECK", "expirationDays": 7}""")
            }

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText().contains("linkToken") shouldBe true
        }
    }
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: FAIL

**Step 3: Add share DTOs and routes**

Add DTOs to ShoppingListRoutes.kt:
```kotlin
@Serializable
data class CreateShareRequest(
    val type: String,
    val accountId: String? = null,
    val permission: String,
    val expirationDays: Int = 7,
)

@Serializable
data class ShareResponse(
    val id: String,
    val type: String,
    val accountId: String?,
    val accountEmail: String?,
    val linkToken: String?,
    val permission: String,
    val expiresAt: String?,
    val createdAt: String,
)
```

Add share routes inside `route("/{id}")` after items:
```kotlin
                route("/shares") {
                    get {
                        val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)

                        val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@get call.respond(HttpStatusCode.BadRequest)

                        val list = listService.findById(listId) ?: return@get call.respond(HttpStatusCode.NotFound)
                        val isOwner = transaction { list.owner.id.value == accountId }
                        if (!isOwner) return@get call.respond(HttpStatusCode.Forbidden)

                        val shares = shareService.findByListId(listId)
                        call.respond(shares.map { share ->
                            transaction {
                                ShareResponse(
                                    id = share.id.value.toString(),
                                    type = share.type.name,
                                    accountId = share.account?.id?.value?.toString(),
                                    accountEmail = share.account?.email,
                                    linkToken = share.linkToken,
                                    permission = share.permission.name,
                                    expiresAt = share.expiresAt?.toString(),
                                    createdAt = share.createdAt.toString(),
                                )
                            }
                        })
                    }

                    post {
                        val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)

                        val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@post call.respond(HttpStatusCode.BadRequest)

                        val list = listService.findById(listId) ?: return@post call.respond(HttpStatusCode.NotFound)
                        val isOwner = transaction { list.owner.id.value == accountId }
                        if (!isOwner) return@post call.respond(HttpStatusCode.Forbidden)

                        val request = call.receive<CreateShareRequest>()
                        val permission = runCatching { SharePermission.valueOf(request.permission) }.getOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest)

                        val share = when (request.type) {
                            "USER" -> {
                                val targetId = request.accountId?.let { UUID.fromString(it) }
                                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                                if (targetId == accountId) {
                                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot share with yourself"))
                                }
                                shareService.createUserShare(listId, targetId, permission)
                            }
                            "LINK" -> shareService.createLinkShare(listId, permission, request.expirationDays)
                            else -> return@post call.respond(HttpStatusCode.BadRequest)
                        }

                        call.respond(HttpStatusCode.Created, transaction {
                            ShareResponse(
                                id = share.id.value.toString(),
                                type = share.type.name,
                                accountId = share.account?.id?.value?.toString(),
                                accountEmail = share.account?.email,
                                linkToken = share.linkToken,
                                permission = share.permission.name,
                                expiresAt = share.expiresAt?.toString(),
                                createdAt = share.createdAt.toString(),
                            )
                        })
                    }

                    delete("/{shareId}") {
                        val accountId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                        val listId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)

                        val shareId = call.parameters["shareId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)

                        val list = listService.findById(listId) ?: return@delete call.respond(HttpStatusCode.NotFound)
                        val isOwner = transaction { list.owner.id.value == accountId }
                        if (!isOwner) return@delete call.respond(HttpStatusCode.Forbidden)

                        shareService.delete(shareId)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.ShoppingListRoutesTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/ShoppingListRoutes.kt \
        backend/src/test/kotlin/no/shoppinglist/routes/ShoppingListRoutesTest.kt
git commit -m "feat: add share management routes"
```

---

## Task 11: Public Shared Access Route

**Files:**
- Create: `backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt`
- Create: `backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt`

**Step 1: Write failing tests**

```kotlin
package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import no.shoppinglist.service.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class SharedAccessRoutesTest : FunSpec({
    lateinit var db: Database
    lateinit var listService: ShoppingListService
    lateinit var itemService: ListItemService
    lateinit var shareService: ListShareService
    lateinit var testAccountId: UUID

    fun Application.testModule() {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        routing {
            sharedAccessRoutes(listService, itemService, shareService)
        }
    }

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.create(Accounts, Households, HouseholdMemberships, ShoppingLists, ListItems, ListShares)
        }
        listService = ShoppingListService(db)
        itemService = ListItemService(db)
        shareService = ListShareService(db)
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
            SchemaUtils.drop(ListShares, ListItems, ShoppingLists, HouseholdMemberships, Households, Accounts)
        }
    }

    test("GET /shared/:token returns list for valid token") {
        testApplication {
            application { testModule() }
            val list = listService.create("Shared List", testAccountId, null, false)
            val share = shareService.createLinkShare(list.id.value, SharePermission.READ, 7)
            val token = transaction(db) { share.linkToken!! }

            val response = client.get("/shared/$token")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().contains("Shared List") shouldBe true
        }
    }

    test("GET /shared/:token returns 410 for expired link") {
        testApplication {
            application { testModule() }
            val list = listService.create("Expired List", testAccountId, null, false)
            val share = shareService.createLinkShare(list.id.value, SharePermission.READ, -1)
            val token = transaction(db) { share.linkToken!! }

            val response = client.get("/shared/$token")

            response.status shouldBe HttpStatusCode.Gone
        }
    }

    test("GET /shared/:token returns 404 for invalid token") {
        testApplication {
            application { testModule() }

            val response = client.get("/shared/invalidtoken12345678901234567890")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.SharedAccessRoutesTest" -q`
Expected: FAIL

**Step 3: Implement SharedAccessRoutes**

```kotlin
package no.shoppinglist.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.shoppinglist.service.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class SharedListResponse(
    val id: String,
    val name: String,
    val permission: String,
    val items: List<SharedItemResponse>,
)

@Serializable
data class SharedItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
)

fun Route.sharedAccessRoutes(
    listService: ShoppingListService,
    itemService: ListItemService,
    shareService: ListShareService,
) {
    route("/shared/{token}") {
        get {
            val token = call.parameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val share = shareService.findByToken(token)
            if (share == null) {
                val expiredShare = transaction {
                    no.shoppinglist.domain.ListShare
                        .find { no.shoppinglist.domain.ListShares.linkToken eq token }
                        .firstOrNull()
                }
                if (expiredShare != null) {
                    return@get call.respond(HttpStatusCode.Gone, mapOf("error" to "This link has expired"))
                }
                return@get call.respond(HttpStatusCode.NotFound)
            }

            val list = transaction { share.list }
            val items = itemService.findByListId(list.id.value)

            call.respond(transaction {
                SharedListResponse(
                    id = list.id.value.toString(),
                    name = list.name,
                    permission = share.permission.name,
                    items = items.map { item ->
                        SharedItemResponse(
                            id = item.id.value.toString(),
                            name = item.name,
                            quantity = item.quantity,
                            unit = item.unit,
                            isChecked = item.isChecked,
                        )
                    },
                )
            })
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "no.shoppinglist.routes.SharedAccessRoutesTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/routes/SharedAccessRoutes.kt \
        backend/src/test/kotlin/no/shoppinglist/routes/SharedAccessRoutesTest.kt
git commit -m "feat: add public shared access route with expiration check"
```

---

## Task 12: Register Routes in Application

**Files:**
- Modify: `backend/src/main/kotlin/no/shoppinglist/Application.kt`

**Step 1: Read current Application.kt**

Check current file structure.

**Step 2: Add service instantiation and route registration**

Add after existing service instantiations:
```kotlin
val shoppingListService = ShoppingListService(db)
val listItemService = ListItemService(db)
val listShareService = ListShareService(db)
```

Add route registrations in routing block:
```kotlin
shoppingListRoutes(shoppingListService, listItemService, listShareService, householdService)
sharedAccessRoutes(shoppingListService, listItemService, listShareService)
```

**Step 3: Run all backend tests**

Run: `cd backend && ./gradlew test -q`
Expected: All tests pass

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/no/shoppinglist/Application.kt
git commit -m "feat: register shopping list and shared access routes"
```

---

## Task 13: Frontend - Lists Redux Slice

**Files:**
- Create: `web/src/store/listsSlice.ts`
- Modify: `web/src/store/store.ts`
- Create: `web/src/store/listsSlice.test.ts`

**Step 1: Write failing test**

```typescript
import { describe, it, expect } from 'vitest';
import listsReducer, {
  setLists,
  addList,
  updateList,
  removeList,
  setItems,
  addItem,
  updateItem,
  removeItem,
  toggleItemCheck,
  ListsState,
} from './listsSlice';

describe('listsSlice', () => {
  const initialState: ListsState = {
    items: [],
    currentListId: null,
    currentListItems: [],
    isLoading: false,
    error: null,
  };

  it('should set lists', () => {
    const lists = [{ id: '1', name: 'Groceries', householdId: null, isPersonal: false, createdAt: '', isOwner: true }];
    const state = listsReducer(initialState, setLists(lists));
    expect(state.items).toEqual(lists);
  });

  it('should add a list', () => {
    const list = { id: '1', name: 'Groceries', householdId: null, isPersonal: false, createdAt: '', isOwner: true };
    const state = listsReducer(initialState, addList(list));
    expect(state.items).toHaveLength(1);
  });

  it('should toggle item check', () => {
    const stateWithItem: ListsState = {
      ...initialState,
      currentListItems: [{ id: '1', name: 'Milk', quantity: 1, unit: null, isChecked: false, checkedByName: null, createdAt: '' }],
    };
    const state = listsReducer(stateWithItem, toggleItemCheck({ id: '1', isChecked: true, checkedByName: 'Test' }));
    expect(state.currentListItems[0].isChecked).toBe(true);
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/store/listsSlice.test.ts`
Expected: FAIL

**Step 3: Implement listsSlice**

```typescript
import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface ShoppingList {
  id: string;
  name: string;
  householdId: string | null;
  isPersonal: boolean;
  createdAt: string;
  isOwner: boolean;
}

export interface ListItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
  checkedByName: string | null;
  createdAt: string;
}

export interface ListsState {
  items: ShoppingList[];
  currentListId: string | null;
  currentListItems: ListItem[];
  isLoading: boolean;
  error: string | null;
}

const initialState: ListsState = {
  items: [],
  currentListId: null,
  currentListItems: [],
  isLoading: false,
  error: null,
};

const listsSlice = createSlice({
  name: 'lists',
  initialState,
  reducers: {
    setLists(state, action: PayloadAction<ShoppingList[]>) {
      state.items = action.payload;
      state.error = null;
    },
    addList(state, action: PayloadAction<ShoppingList>) {
      state.items.push(action.payload);
    },
    updateList(state, action: PayloadAction<{ id: string; name: string; isPersonal: boolean }>) {
      const list = state.items.find((l) => l.id === action.payload.id);
      if (list) {
        list.name = action.payload.name;
        list.isPersonal = action.payload.isPersonal;
      }
    },
    removeList(state, action: PayloadAction<string>) {
      state.items = state.items.filter((l) => l.id !== action.payload);
    },
    setCurrentList(state, action: PayloadAction<string | null>) {
      state.currentListId = action.payload;
    },
    setItems(state, action: PayloadAction<ListItem[]>) {
      state.currentListItems = action.payload;
    },
    addItem(state, action: PayloadAction<ListItem>) {
      state.currentListItems.push(action.payload);
    },
    updateItem(state, action: PayloadAction<ListItem>) {
      const index = state.currentListItems.findIndex((i) => i.id === action.payload.id);
      if (index !== -1) {
        state.currentListItems[index] = action.payload;
      }
    },
    removeItem(state, action: PayloadAction<string>) {
      state.currentListItems = state.currentListItems.filter((i) => i.id !== action.payload);
    },
    toggleItemCheck(state, action: PayloadAction<{ id: string; isChecked: boolean; checkedByName: string | null }>) {
      const item = state.currentListItems.find((i) => i.id === action.payload.id);
      if (item) {
        item.isChecked = action.payload.isChecked;
        item.checkedByName = action.payload.checkedByName;
      }
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
  setLists,
  addList,
  updateList,
  removeList,
  setCurrentList,
  setItems,
  addItem,
  updateItem,
  removeItem,
  toggleItemCheck,
  setLoading,
  setError,
} = listsSlice.actions;

export default listsSlice.reducer;
```

**Step 4: Add to store.ts**

```typescript
import listsReducer from './listsSlice';

// In configureStore reducers:
lists: listsReducer,
```

**Step 5: Run test to verify it passes**

Run: `cd web && npm test -- --run src/store/listsSlice.test.ts`
Expected: PASS

**Step 6: Commit**

```bash
git add web/src/store/listsSlice.ts web/src/store/listsSlice.test.ts web/src/store/store.ts
git commit -m "feat: add lists Redux slice with items support"
```

---

## Task 14: Frontend - ShoppingListsPage Component

**Files:**
- Create: `web/src/components/ShoppingListsPage.tsx`
- Create: `web/src/components/ShoppingListsPage.test.tsx`

**Step 1: Write failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import ShoppingListsPage from './ShoppingListsPage';
import listsReducer from '../store/listsSlice';
import householdsReducer from '../store/householdsSlice';

const createTestStore = (lists = []) =>
  configureStore({
    reducer: { lists: listsReducer, households: householdsReducer },
    preloadedState: {
      lists: { items: lists, currentListId: null, currentListItems: [], isLoading: false, error: null },
      households: { items: [], isLoading: false, error: null },
    },
  });

describe('ShoppingListsPage', () => {
  it('renders empty state when no lists', () => {
    render(
      <Provider store={createTestStore()}>
        <ShoppingListsPage onSelectList={vi.fn()} onCreateClick={vi.fn()} />
      </Provider>
    );
    expect(screen.getByText(/no shopping lists/i)).toBeInTheDocument();
  });

  it('renders lists when available', () => {
    const lists = [{ id: '1', name: 'Groceries', householdId: null, isPersonal: false, createdAt: '', isOwner: true }];
    render(
      <Provider store={createTestStore(lists)}>
        <ShoppingListsPage onSelectList={vi.fn()} onCreateClick={vi.fn()} />
      </Provider>
    );
    expect(screen.getByText('Groceries')).toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/components/ShoppingListsPage.test.tsx`
Expected: FAIL

**Step 3: Implement component**

```typescript
import { useAppSelector } from '../store/hooks';

interface ShoppingListsPageProps {
  onSelectList: (id: string) => void;
  onCreateClick: () => void;
}

export default function ShoppingListsPage({ onSelectList, onCreateClick }: ShoppingListsPageProps) {
  const { items, isLoading } = useAppSelector((state) => state.lists);
  const households = useAppSelector((state) => state.households.items);

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
      </div>
    );
  }

  const personalLists = items.filter((l) => !l.householdId);
  const listsByHousehold = households.map((h) => ({
    household: h,
    lists: items.filter((l) => l.householdId === h.id),
  }));

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">Shopping Lists</h2>
        <button
          onClick={onCreateClick}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          Create List
        </button>
      </div>

      {items.length === 0 ? (
        <div className="rounded-lg border-2 border-dashed border-gray-300 p-12 text-center">
          <h3 className="text-sm font-semibold text-gray-900">No shopping lists yet</h3>
          <p className="mt-1 text-sm text-gray-500">Create your first shopping list to get started.</p>
          <button
            onClick={onCreateClick}
            className="mt-4 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
          >
            Create your first list
          </button>
        </div>
      ) : (
        <div className="space-y-8">
          {personalLists.length > 0 && (
            <div>
              <h3 className="mb-4 text-lg font-medium text-gray-700">Personal Lists</h3>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {personalLists.map((list) => (
                  <button
                    key={list.id}
                    onClick={() => onSelectList(list.id)}
                    className="rounded-lg bg-white p-6 text-left shadow transition hover:shadow-md"
                  >
                    <h4 className="font-semibold text-gray-900">{list.name}</h4>
                    {list.isPersonal && (
                      <span className="mt-2 inline-block rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-600">
                        Private
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {listsByHousehold.map(
            ({ household, lists }) =>
              lists.length > 0 && (
                <div key={household.id}>
                  <h3 className="mb-4 text-lg font-medium text-gray-700">{household.name}</h3>
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {lists.map((list) => (
                      <button
                        key={list.id}
                        onClick={() => onSelectList(list.id)}
                        className="rounded-lg bg-white p-6 text-left shadow transition hover:shadow-md"
                      >
                        <h4 className="font-semibold text-gray-900">{list.name}</h4>
                        {list.isPersonal && (
                          <span className="mt-2 inline-block rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-600">
                            Private
                          </span>
                        )}
                      </button>
                    ))}
                  </div>
                </div>
              )
          )}
        </div>
      )}
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd web && npm test -- --run src/components/ShoppingListsPage.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/ShoppingListsPage.tsx web/src/components/ShoppingListsPage.test.tsx
git commit -m "feat: add ShoppingListsPage component"
```

---

## Task 15: Frontend - ShoppingListView Component

**Files:**
- Create: `web/src/components/ShoppingListView.tsx`
- Create: `web/src/components/ShoppingListView.test.tsx`

**Step 1: Write failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import ShoppingListView from './ShoppingListView';
import listsReducer from '../store/listsSlice';

const createTestStore = (items = []) =>
  configureStore({
    reducer: { lists: listsReducer },
    preloadedState: {
      lists: {
        items: [{ id: '1', name: 'Groceries', householdId: null, isPersonal: false, createdAt: '', isOwner: true }],
        currentListId: '1',
        currentListItems: items,
        isLoading: false,
        error: null,
      },
    },
  });

describe('ShoppingListView', () => {
  it('renders list name and items', () => {
    const items = [{ id: '1', name: 'Milk', quantity: 2, unit: 'liters', isChecked: false, checkedByName: null, createdAt: '' }];
    render(
      <Provider store={createTestStore(items)}>
        <ShoppingListView listId="1" onBack={vi.fn()} />
      </Provider>
    );
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByText('2 liters')).toBeInTheDocument();
  });

  it('shows checked items with strikethrough', () => {
    const items = [{ id: '1', name: 'Milk', quantity: 1, unit: null, isChecked: true, checkedByName: 'Test', createdAt: '' }];
    render(
      <Provider store={createTestStore(items)}>
        <ShoppingListView listId="1" onBack={vi.fn()} />
      </Provider>
    );
    const item = screen.getByText('Milk');
    expect(item).toHaveClass('line-through');
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/components/ShoppingListView.test.tsx`
Expected: FAIL

**Step 3: Implement component**

```typescript
import { useState } from 'react';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { addItem, toggleItemCheck, removeItem } from '../store/listsSlice';

interface ShoppingListViewProps {
  listId: string;
  onBack: () => void;
}

export default function ShoppingListView({ listId, onBack }: ShoppingListViewProps) {
  const dispatch = useAppDispatch();
  const list = useAppSelector((state) => state.lists.items.find((l) => l.id === listId));
  const items = useAppSelector((state) => state.lists.currentListItems);
  const [newItemName, setNewItemName] = useState('');
  const [newItemQuantity, setNewItemQuantity] = useState('1');
  const [newItemUnit, setNewItemUnit] = useState('');

  const handleAddItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newItemName.trim()) return;

    const response = await fetch(`/api/lists/${listId}/items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newItemName,
        quantity: parseFloat(newItemQuantity) || 1,
        unit: newItemUnit || null,
      }),
    });

    if (response.ok) {
      const item = await response.json();
      dispatch(addItem(item));
      setNewItemName('');
      setNewItemQuantity('1');
      setNewItemUnit('');
    }
  };

  const handleToggleCheck = async (itemId: string) => {
    const response = await fetch(`/api/lists/${listId}/items/${itemId}/check`, { method: 'POST' });
    if (response.ok) {
      const updated = await response.json();
      dispatch(toggleItemCheck({ id: itemId, isChecked: updated.isChecked, checkedByName: updated.checkedByName }));
    }
  };

  const handleDeleteItem = async (itemId: string) => {
    const response = await fetch(`/api/lists/${listId}/items/${itemId}`, { method: 'DELETE' });
    if (response.ok) {
      dispatch(removeItem(itemId));
    }
  };

  if (!list) return null;

  const uncheckedItems = items.filter((i) => !i.isChecked);
  const checkedItems = items.filter((i) => i.isChecked);

  return (
    <div>
      <div className="mb-6 flex items-center gap-4">
        <button onClick={onBack} className="text-gray-600 hover:text-gray-900">
          ← Back
        </button>
        <h2 className="text-xl font-semibold text-gray-900">{list.name}</h2>
      </div>

      <form onSubmit={handleAddItem} className="mb-6 flex gap-2">
        <input
          type="text"
          value={newItemName}
          onChange={(e) => setNewItemName(e.target.value)}
          placeholder="Add item..."
          className="flex-1 rounded-md border border-gray-300 px-3 py-2"
        />
        <input
          type="number"
          value={newItemQuantity}
          onChange={(e) => setNewItemQuantity(e.target.value)}
          className="w-20 rounded-md border border-gray-300 px-3 py-2"
          min="0.1"
          step="0.1"
        />
        <input
          type="text"
          value={newItemUnit}
          onChange={(e) => setNewItemUnit(e.target.value)}
          placeholder="unit"
          className="w-24 rounded-md border border-gray-300 px-3 py-2"
        />
        <button type="submit" className="rounded-md bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-500">
          Add
        </button>
      </form>

      <div className="space-y-2">
        {uncheckedItems.map((item) => (
          <div key={item.id} className="flex items-center gap-3 rounded-lg bg-white p-4 shadow">
            <input
              type="checkbox"
              checked={item.isChecked}
              onChange={() => handleToggleCheck(item.id)}
              className="h-5 w-5 rounded border-gray-300"
            />
            <span className="flex-1">{item.name}</span>
            <span className="text-gray-500">
              {item.quantity} {item.unit}
            </span>
            <button onClick={() => handleDeleteItem(item.id)} className="text-red-600 hover:text-red-800">
              ×
            </button>
          </div>
        ))}

        {checkedItems.length > 0 && (
          <div className="mt-6">
            <h3 className="mb-2 text-sm font-medium text-gray-500">Checked Items</h3>
            {checkedItems.map((item) => (
              <div key={item.id} className="flex items-center gap-3 rounded-lg bg-gray-50 p-4">
                <input
                  type="checkbox"
                  checked={item.isChecked}
                  onChange={() => handleToggleCheck(item.id)}
                  className="h-5 w-5 rounded border-gray-300"
                />
                <span className="flex-1 text-gray-500 line-through">{item.name}</span>
                <span className="text-gray-400">
                  {item.quantity} {item.unit}
                </span>
                <button onClick={() => handleDeleteItem(item.id)} className="text-red-400 hover:text-red-600">
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd web && npm test -- --run src/components/ShoppingListView.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/ShoppingListView.tsx web/src/components/ShoppingListView.test.tsx
git commit -m "feat: add ShoppingListView with items and check toggle"
```

---

## Task 16: Frontend - CreateListModal Component

**Files:**
- Create: `web/src/components/CreateListModal.tsx`
- Create: `web/src/components/CreateListModal.test.tsx`

**Step 1: Write failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import CreateListModal from './CreateListModal';
import listsReducer from '../store/listsSlice';
import householdsReducer from '../store/householdsSlice';

const createTestStore = () =>
  configureStore({
    reducer: { lists: listsReducer, households: householdsReducer },
    preloadedState: {
      lists: { items: [], currentListId: null, currentListItems: [], isLoading: false, error: null },
      households: { items: [{ id: '1', name: 'Home', createdAt: '', memberCount: 1, isOwner: true }], isLoading: false, error: null },
    },
  });

describe('CreateListModal', () => {
  it('renders form fields', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateListModal onClose={vi.fn()} />
      </Provider>
    );
    expect(screen.getByLabelText(/list name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/household/i)).toBeInTheDocument();
  });

  it('calls onClose when cancel clicked', () => {
    const onClose = vi.fn();
    render(
      <Provider store={createTestStore()}>
        <CreateListModal onClose={onClose} />
      </Provider>
    );
    fireEvent.click(screen.getByText(/cancel/i));
    expect(onClose).toHaveBeenCalled();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/components/CreateListModal.test.tsx`
Expected: FAIL

**Step 3: Implement component**

```typescript
import { useState } from 'react';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { addList } from '../store/listsSlice';

interface CreateListModalProps {
  onClose: () => void;
}

export default function CreateListModal({ onClose }: CreateListModalProps) {
  const dispatch = useAppDispatch();
  const households = useAppSelector((state) => state.households.items);
  const [name, setName] = useState('');
  const [householdId, setHouseholdId] = useState('');
  const [isPersonal, setIsPersonal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setIsSubmitting(true);
    const response = await fetch('/api/lists', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        householdId: householdId || null,
        isPersonal,
      }),
    });

    if (response.ok) {
      const list = await response.json();
      dispatch(addList(list));
      onClose();
    }
    setIsSubmitting(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold">Create Shopping List</h2>
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="name" className="block text-sm font-medium text-gray-700">
              List Name
            </label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
              required
            />
          </div>
          <div className="mb-4">
            <label htmlFor="household" className="block text-sm font-medium text-gray-700">
              Household (optional)
            </label>
            <select
              id="household"
              value={householdId}
              onChange={(e) => setHouseholdId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
            >
              <option value="">Personal (no household)</option>
              {households.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.name}
                </option>
              ))}
            </select>
          </div>
          {householdId && (
            <div className="mb-4">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={isPersonal}
                  onChange={(e) => setIsPersonal(e.target.checked)}
                  className="rounded border-gray-300"
                />
                <span className="text-sm text-gray-700">Make private (only visible to you)</span>
              </label>
            </div>
          )}
          <div className="flex justify-end gap-3">
            <button type="button" onClick={onClose} className="px-4 py-2 text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-500 disabled:opacity-50"
            >
              {isSubmitting ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd web && npm test -- --run src/components/CreateListModal.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/CreateListModal.tsx web/src/components/CreateListModal.test.tsx
git commit -m "feat: add CreateListModal component"
```

---

## Task 17: Frontend - ShareListModal Component

**Files:**
- Create: `web/src/components/ShareListModal.tsx`
- Create: `web/src/components/ShareListModal.test.tsx`

**Step 1: Write failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import ShareListModal from './ShareListModal';

describe('ShareListModal', () => {
  it('renders share tabs', () => {
    render(<ShareListModal listId="1" onClose={vi.fn()} />);
    expect(screen.getByText(/share with user/i)).toBeInTheDocument();
    expect(screen.getByText(/share link/i)).toBeInTheDocument();
  });

  it('shows expiration field for link share', () => {
    render(<ShareListModal listId="1" onClose={vi.fn()} />);
    screen.getByText(/share link/i).click();
    expect(screen.getByLabelText(/expires in/i)).toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/components/ShareListModal.test.tsx`
Expected: FAIL

**Step 3: Implement component**

```typescript
import { useState } from 'react';

interface ShareListModalProps {
  listId: string;
  onClose: () => void;
}

export default function ShareListModal({ listId, onClose }: ShareListModalProps) {
  const [tab, setTab] = useState<'user' | 'link'>('user');
  const [email, setEmail] = useState('');
  const [permission, setPermission] = useState('READ');
  const [expirationDays, setExpirationDays] = useState(7);
  const [generatedLink, setGeneratedLink] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleShareWithUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError('');

    const response = await fetch(`/api/lists/${listId}/shares`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'USER', email, permission }),
    });

    if (response.ok) {
      onClose();
    } else {
      const data = await response.json();
      setError(data.error || 'Failed to share');
    }
    setIsSubmitting(false);
  };

  const handleCreateLink = async () => {
    setIsSubmitting(true);
    setError('');

    const response = await fetch(`/api/lists/${listId}/shares`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'LINK', permission, expirationDays }),
    });

    if (response.ok) {
      const data = await response.json();
      setGeneratedLink(`${window.location.origin}/shared/${data.linkToken}`);
    } else {
      const data = await response.json();
      setError(data.error || 'Failed to create link');
    }
    setIsSubmitting(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold">Share List</h2>

        <div className="mb-4 flex gap-2">
          <button
            onClick={() => setTab('user')}
            className={`flex-1 rounded-md px-4 py-2 ${tab === 'user' ? 'bg-indigo-600 text-white' : 'bg-gray-100'}`}
          >
            Share with User
          </button>
          <button
            onClick={() => setTab('link')}
            className={`flex-1 rounded-md px-4 py-2 ${tab === 'link' ? 'bg-indigo-600 text-white' : 'bg-gray-100'}`}
          >
            Share Link
          </button>
        </div>

        {error && <div className="mb-4 rounded-md bg-red-50 p-3 text-red-700">{error}</div>}

        {tab === 'user' ? (
          <form onSubmit={handleShareWithUser}>
            <div className="mb-4">
              <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                User Email
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
                required
              />
            </div>
            <div className="mb-4">
              <label htmlFor="permission" className="block text-sm font-medium text-gray-700">
                Permission
              </label>
              <select
                id="permission"
                value={permission}
                onChange={(e) => setPermission(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
              >
                <option value="READ">Read only</option>
                <option value="CHECK">Can check items</option>
                <option value="WRITE">Full access</option>
              </select>
            </div>
            <div className="flex justify-end gap-3">
              <button type="button" onClick={onClose} className="px-4 py-2 text-gray-600">
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                className="rounded-md bg-indigo-600 px-4 py-2 text-white disabled:opacity-50"
              >
                Share
              </button>
            </div>
          </form>
        ) : (
          <div>
            <div className="mb-4">
              <label htmlFor="linkPermission" className="block text-sm font-medium text-gray-700">
                Permission
              </label>
              <select
                id="linkPermission"
                value={permission}
                onChange={(e) => setPermission(e.target.value)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
              >
                <option value="READ">Read only</option>
                <option value="CHECK">Can check items</option>
                <option value="WRITE">Full access</option>
              </select>
            </div>
            <div className="mb-4">
              <label htmlFor="expiration" className="block text-sm font-medium text-gray-700">
                Expires in (days)
              </label>
              <input
                id="expiration"
                type="number"
                value={expirationDays}
                onChange={(e) => setExpirationDays(parseInt(e.target.value) || 7)}
                min="1"
                max="365"
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2"
              />
            </div>

            {generatedLink ? (
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700">Share Link</label>
                <div className="mt-1 flex gap-2">
                  <input
                    type="text"
                    value={generatedLink}
                    readOnly
                    className="flex-1 rounded-md border border-gray-300 bg-gray-50 px-3 py-2"
                  />
                  <button
                    onClick={() => navigator.clipboard.writeText(generatedLink)}
                    className="rounded-md bg-gray-200 px-4 py-2 hover:bg-gray-300"
                  >
                    Copy
                  </button>
                </div>
              </div>
            ) : null}

            <div className="flex justify-end gap-3">
              <button type="button" onClick={onClose} className="px-4 py-2 text-gray-600">
                {generatedLink ? 'Done' : 'Cancel'}
              </button>
              {!generatedLink && (
                <button
                  onClick={handleCreateLink}
                  disabled={isSubmitting}
                  className="rounded-md bg-indigo-600 px-4 py-2 text-white disabled:opacity-50"
                >
                  Create Link
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd web && npm test -- --run src/components/ShareListModal.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/ShareListModal.tsx web/src/components/ShareListModal.test.tsx
git commit -m "feat: add ShareListModal with user and link sharing"
```

---

## Task 18: Frontend - SharedListView for Public Access

**Files:**
- Create: `web/src/components/SharedListView.tsx`
- Create: `web/src/components/SharedListView.test.tsx`

**Step 1: Write failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import SharedListView from './SharedListView';

global.fetch = vi.fn();

describe('SharedListView', () => {
  it('shows loading state initially', () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ id: '1', name: 'Shared', permission: 'READ', items: [] }),
    });

    render(<SharedListView token="abc123" />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows expired message for 410 response', async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 410,
    });

    render(<SharedListView token="expired" />);
    expect(await screen.findByText(/expired/i)).toBeInTheDocument();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd web && npm test -- --run src/components/SharedListView.test.tsx`
Expected: FAIL

**Step 3: Implement component**

```typescript
import { useEffect, useState } from 'react';

interface SharedItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
}

interface SharedList {
  id: string;
  name: string;
  permission: string;
  items: SharedItem[];
}

interface SharedListViewProps {
  token: string;
}

export default function SharedListView({ token }: SharedListViewProps) {
  const [list, setList] = useState<SharedList | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<'expired' | 'notfound' | null>(null);

  useEffect(() => {
    fetch(`/api/shared/${token}`)
      .then((res) => {
        if (res.status === 410) {
          setError('expired');
          return null;
        }
        if (!res.ok) {
          setError('notfound');
          return null;
        }
        return res.json();
      })
      .then((data) => {
        if (data) setList(data);
        setLoading(false);
      });
  }, [token]);

  const handleToggleCheck = async (itemId: string) => {
    if (list?.permission === 'READ') return;

    const response = await fetch(`/api/shared/${token}/items/${itemId}/check`, { method: 'POST' });
    if (response.ok) {
      const updated = await response.json();
      setList((prev) =>
        prev
          ? {
              ...prev,
              items: prev.items.map((i) => (i.id === itemId ? { ...i, isChecked: updated.isChecked } : i)),
            }
          : null
      );
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-gray-500">Loading...</p>
      </div>
    );
  }

  if (error === 'expired') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900">Link Expired</h1>
          <p className="mt-2 text-gray-500">This shared link has expired and is no longer accessible.</p>
        </div>
      </div>
    );
  }

  if (error === 'notfound' || !list) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900">Not Found</h1>
          <p className="mt-2 text-gray-500">This shared list could not be found.</p>
        </div>
      </div>
    );
  }

  const canCheck = list.permission === 'CHECK' || list.permission === 'WRITE';

  return (
    <div className="mx-auto max-w-2xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{list.name}</h1>
        <p className="text-sm text-gray-500">
          {list.permission === 'READ' && 'View only'}
          {list.permission === 'CHECK' && 'You can check items'}
          {list.permission === 'WRITE' && 'Full access'}
        </p>
      </div>

      <div className="space-y-2">
        {list.items.map((item) => (
          <div
            key={item.id}
            className={`flex items-center gap-3 rounded-lg p-4 ${item.isChecked ? 'bg-gray-50' : 'bg-white shadow'}`}
          >
            <input
              type="checkbox"
              checked={item.isChecked}
              onChange={() => handleToggleCheck(item.id)}
              disabled={!canCheck}
              className="h-5 w-5 rounded border-gray-300 disabled:opacity-50"
            />
            <span className={item.isChecked ? 'flex-1 text-gray-500 line-through' : 'flex-1'}>{item.name}</span>
            <span className="text-gray-400">
              {item.quantity} {item.unit}
            </span>
          </div>
        ))}

        {list.items.length === 0 && <p className="text-center text-gray-500">No items in this list</p>}
      </div>
    </div>
  );
}
```

**Step 4: Run test to verify it passes**

Run: `cd web && npm test -- --run src/components/SharedListView.test.tsx`
Expected: PASS

**Step 5: Commit**

```bash
git add web/src/components/SharedListView.tsx web/src/components/SharedListView.test.tsx
git commit -m "feat: add SharedListView for public link access"
```

---

## Task 19: Integrate Lists into App.tsx

**Files:**
- Modify: `web/src/App.tsx`

**Step 1: Read current App.tsx**

Check current structure and integrate shopping lists.

**Step 2: Add list state and navigation**

Add imports and state:
```typescript
import ShoppingListsPage from './components/ShoppingListsPage';
import ShoppingListView from './components/ShoppingListView';
import CreateListModal from './components/CreateListModal';
import { setLists, setItems, setLoading } from './store/listsSlice';
```

Add state:
```typescript
const [view, setView] = useState<'households' | 'lists' | 'list-detail'>('households');
const [selectedListId, setSelectedListId] = useState<string | null>(null);
const [showCreateListModal, setShowCreateListModal] = useState(false);
```

Add fetch function for lists:
```typescript
const fetchLists = async () => {
  dispatch(setLoading(true));
  const response = await fetch('/api/lists');
  if (response.ok) {
    const lists = await response.json();
    dispatch(setLists(lists));
  }
  dispatch(setLoading(false));
};
```

Add view rendering:
```typescript
{view === 'lists' && (
  <ShoppingListsPage
    onSelectList={(id) => {
      setSelectedListId(id);
      fetchListItems(id);
      setView('list-detail');
    }}
    onCreateClick={() => setShowCreateListModal(true)}
  />
)}

{view === 'list-detail' && selectedListId && (
  <ShoppingListView
    listId={selectedListId}
    onBack={() => {
      setSelectedListId(null);
      setView('lists');
    }}
  />
)}

{showCreateListModal && (
  <CreateListModal onClose={() => setShowCreateListModal(false)} />
)}
```

**Step 3: Run all frontend tests**

Run: `cd web && npm test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add web/src/App.tsx
git commit -m "feat: integrate shopping lists into main App"
```

---

## Task 20: Final Verification

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test -q`
Expected: All tests pass

**Step 2: Run all frontend tests**

Run: `cd web && npm test`
Expected: All tests pass

**Step 3: Run detekt**

Run: `cd backend && ./gradlew detekt`
Expected: No issues (fix any that appear)

**Step 4: Run ESLint**

Run: `cd web && npm run lint`
Expected: No issues (fix any that appear)

**Step 5: Final commit if any fixes**

```bash
git add -A
git commit -m "fix: resolve linting issues"
```

---

**Plan complete.** 20 tasks covering backend services, routes, and frontend components for shopping lists with items and sharing.
