package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.ShoppingListService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class SharedAccessRoutesTest :
    FunSpec({
        lateinit var db: Database
        lateinit var accountService: AccountService
        lateinit var shoppingListService: ShoppingListService
        lateinit var listItemService: ListItemService
        lateinit var listShareService: ListShareService

        beforeSpec {
            db = TestDatabaseConfig.init()
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
            }
            accountService = AccountService(db)
            shoppingListService = ShoppingListService(db)
            listItemService = ListItemService(db)
            listShareService = ListShareService(db)
        }

        afterTest {
            transaction(db) {
                ListShares.deleteAll()
                ListItems.deleteAll()
                RecurringItems.deleteAll()
                ShoppingLists.deleteAll()
                HouseholdMemberships.deleteAll()
                Households.deleteAll()
                Accounts.deleteAll()
            }
        }

        afterSpec {
            transaction(db) {
                SchemaUtils.drop(
                    ListShares,
                    ListItems,
                    RecurringItems,
                    ShoppingLists,
                    HouseholdMemberships,
                    Households,
                    Accounts,
                )
            }
        }

        fun configureContentNegotiation(app: Application) {
            app.install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

        fun testModule(app: Application) {
            configureContentNegotiation(app)
            app.routing {
                sharedAccessRoutes(listShareService, listItemService)
            }
        }

        test("GET /shared/:token returns list for valid token") {
            testApplication {
                application { testModule(this) }

                // Create account and list
                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Groceries",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )

                // Add items to the list
                val item1 =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Milk",
                        quantity = 2.0,
                        unit = "L",
                        barcode = null,
                        createdById = account.id.value,
                    )
                val item2 =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Bread",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )

                // Create link share
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.READ,
                        expirationHours = 168,
                    )

                // Get shared list via token
                val response = client.get("/shared/${share.linkToken}")

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["id"]?.jsonPrimitive?.content shouldBe list.id.value.toString()
                json["name"]?.jsonPrimitive?.content shouldBe "Groceries"
                json["permission"]?.jsonPrimitive?.content shouldBe "READ"

                val items = json["items"]?.jsonArray
                items?.size shouldBe 2

                // Verify item structure
                val itemNames = items?.map { it.jsonObject["name"]?.jsonPrimitive?.content }
                itemNames?.contains("Milk") shouldBe true
                itemNames?.contains("Bread") shouldBe true
            }
        }

        test("GET /shared/:token returns 410 for expired link") {
            testApplication {
                application { testModule(this) }

                // Create account and list
                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Expired List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )

                // Create link share with -1 days (already expired)
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.READ,
                        expirationHours = -1,
                    )

                // Try to access expired link
                val response = client.get("/shared/${share.linkToken}")

                response.status shouldBe HttpStatusCode.Gone
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["error"]?.jsonPrimitive?.content shouldBe "This link has expired"
            }
        }

        test("GET /shared/:token returns 404 for invalid token") {
            testApplication {
                application { testModule(this) }

                // Fetch with random token
                val randomToken = "nonexistent-token-123456"
                val response = client.get("/shared/$randomToken")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("GET /shared/:token returns correct permission level") {
            testApplication {
                application { testModule(this) }

                // Create account and list
                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Writable List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )

                // Create link share with WRITE permission
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.WRITE,
                        expirationHours = 168,
                    )

                // Get shared list via token
                val response = client.get("/shared/${share.linkToken}")

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["permission"]?.jsonPrimitive?.content shouldBe "WRITE"
            }
        }

        test("GET /shared/:token returns item check status") {
            testApplication {
                application { testModule(this) }

                // Create account and list
                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "List with checked items",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )

                // Add items and check one
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Checked Item",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                listItemService.toggleCheck(item.id.value, account.id.value)

                // Create link share
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.READ,
                        expirationHours = 168,
                    )

                // Get shared list via token
                val response = client.get("/shared/${share.linkToken}")

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val items = json["items"]?.jsonArray
                items?.size shouldBe 1
                items
                    ?.get(0)
                    ?.jsonObject
                    ?.get("isChecked")
                    ?.jsonPrimitive
                    ?.content shouldBe "true"
            }
        }

        test("POST /shared/:token/items/:itemId/check toggles item for CHECK permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Check Test List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Milk",
                        quantity = 2.0,
                        unit = "L",
                        barcode = null,
                        createdById = account.id.value,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.CHECK,
                        expirationHours = 168,
                    )

                val response =
                    client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["isChecked"]?.jsonPrimitive?.content shouldBe "true"
            }
        }

        test("POST /shared/:token/items/:itemId/check returns 403 for READ permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Read Only List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Bread",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.READ,
                        expirationHours = 168,
                    )

                val response =
                    client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }

                response.status shouldBe HttpStatusCode.Forbidden
            }
        }

        test("POST /shared/:token/items/:itemId/check returns 410 for expired token") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Expired Check List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Eggs",
                        quantity = 12.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.CHECK,
                        expirationHours = -1,
                    )

                val response =
                    client.post("/shared/${share.linkToken}/items/${item.id.value}/check") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }

                response.status shouldBe HttpStatusCode.Gone
            }
        }

        test("POST /shared/:token/items adds item for WRITE permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Write Test List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.WRITE,
                        expirationHours = 168,
                    )

                val response =
                    client.post("/shared/${share.linkToken}/items") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Apples","quantity":3.0,"unit":"kg"}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "Apples"
                json["quantity"]?.jsonPrimitive?.content shouldBe "3.0"
                json["unit"]?.jsonPrimitive?.content shouldBe "kg"
                json["isChecked"]?.jsonPrimitive?.content shouldBe "false"
            }
        }

        test("POST /shared/:token/items returns 403 for CHECK permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Check Only List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.CHECK,
                        expirationHours = 168,
                    )

                val response =
                    client.post("/shared/${share.linkToken}/items") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Bananas","quantity":1.0}""")
                    }

                response.status shouldBe HttpStatusCode.Forbidden
            }
        }

        test("PATCH /shared/:token/items/:itemId updates item for WRITE permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Update Test List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Milk",
                        quantity = 1.0,
                        unit = "L",
                        barcode = null,
                        createdById = account.id.value,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.WRITE,
                        expirationHours = 168,
                    )

                val response =
                    client.patch("/shared/${share.linkToken}/items/${item.id.value}") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Oat Milk","quantity":2.0,"unit":"L"}""")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "Oat Milk"
                json["quantity"]?.jsonPrimitive?.content shouldBe "2.0"
                json["unit"]?.jsonPrimitive?.content shouldBe "L"
            }
        }

        test("DELETE /shared/:token/items/:itemId deletes item for WRITE permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Delete Test List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Bread",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.WRITE,
                        expirationHours = 168,
                    )

                val response =
                    client.delete("/shared/${share.linkToken}/items/${item.id.value}")

                response.status shouldBe HttpStatusCode.NoContent
            }
        }

        test("DELETE /shared/:token/items/checked clears checked items for WRITE permission") {
            testApplication {
                application { testModule(this) }

                val account =
                    accountService.createLocal(
                        email = "test@example.com",
                        displayName = "Test User",
                        password = "password123",
                    )
                val list =
                    shoppingListService.create(
                        name = "Clear Checked List",
                        ownerId = account.id.value,
                        householdId = null,
                        isPersonal = false,
                    )
                val item1 =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Checked Item",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                val item2 =
                    listItemService.create(
                        listId = list.id.value,
                        name = "Unchecked Item",
                        quantity = 1.0,
                        unit = null,
                        barcode = null,
                        createdById = account.id.value,
                    )
                // Check item1
                listItemService.toggleCheck(item1.id.value, account.id.value)

                val share =
                    listShareService.createLinkShare(
                        listId = list.id.value,
                        permission = SharePermission.WRITE,
                        expirationHours = 168,
                    )

                val response =
                    client.delete("/shared/${share.linkToken}/items/checked")

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val deletedIds = json["deletedItemIds"]?.jsonArray
                deletedIds?.size shouldBe 1
                deletedIds?.get(0)?.jsonPrimitive?.content shouldBe item1.id.value.toString()
            }
        }
    })
