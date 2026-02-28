package no.shoppinglist.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.config.GoogleAuthConfig
import no.shoppinglist.config.JwtConfig
import no.shoppinglist.config.LocalAuthConfig
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.config.TestValkeyConfig
import no.shoppinglist.config.ValkeyConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ItemHistories
import no.shoppinglist.domain.ListActivities
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.PinnedLists
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.RefreshTokens
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.routes.auth.authRoutes
import no.shoppinglist.routes.shoppinglist.shoppingListRoutes
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ItemHistoryService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.RecurringItemService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.service.TokenBlacklistService
import no.shoppinglist.service.ValkeyService
import no.shoppinglist.websocket.EventBroadcaster
import no.shoppinglist.websocket.WebSocketBroadcastService
import no.shoppinglist.websocket.WebSocketSessionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

@Suppress("LargeClass")
class ShoppingListRoutesTest :
    FunSpec({
        lateinit var db: Database
        lateinit var accountService: AccountService
        lateinit var householdService: HouseholdService
        lateinit var shoppingListService: ShoppingListService
        lateinit var listItemService: ListItemService
        lateinit var listShareService: ListShareService
        lateinit var pinnedListService: PinnedListService
        lateinit var activityService: ActivityService
        lateinit var itemHistoryService: ItemHistoryService
        lateinit var jwtService: JwtService
        lateinit var authConfig: AuthConfig
        lateinit var eventBroadcaster: EventBroadcaster
        lateinit var refreshTokenService: RefreshTokenService
        lateinit var tokenBlacklistService: TokenBlacklistService
        lateinit var recurringItemService: RecurringItemService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(
                    Accounts,
                    Households,
                    HouseholdMemberships,
                    ShoppingLists,
                    ListItems,
                    ListShares,
                    ListActivities,
                    PinnedLists,
                    ItemHistories,
                    Comments,
                    RecurringItems,
                    RefreshTokens,
                )
            }
            authConfig =
                AuthConfig(
                    jwt =
                        JwtConfig(
                            secret = "test-secret-key-for-testing-only",
                            issuer = "shopping-list-test",
                            audience = "shopping-list-users",
                            realm = "Shopping List Test",
                            expirationMinutes = 24,
                        ),
                    google =
                        GoogleAuthConfig(
                            enabled = false,
                            clientId = "",
                            clientSecret = "",
                            callbackUrl = "",
                        ),
                    local = LocalAuthConfig(enabled = true),
                )
            accountService = AccountService(db)
            householdService = HouseholdService(db)
            shoppingListService = ShoppingListService(db)
            listItemService = ListItemService(db)
            listShareService = ListShareService(db)
            pinnedListService = PinnedListService(db)
            activityService = ActivityService(db)
            itemHistoryService = ItemHistoryService(db)
            jwtService = JwtService(authConfig.jwt)
            val valkeyService = ValkeyService(ValkeyConfig(host = "localhost", port = 6379, password = ""))
            val sessionManager = WebSocketSessionManager()
            val broadcastService = WebSocketBroadcastService(valkeyService, sessionManager)
            eventBroadcaster = EventBroadcaster(broadcastService)
            refreshTokenService = RefreshTokenService(db)
            tokenBlacklistService = TestValkeyConfig.createNoOpTokenBlacklistService()
            recurringItemService = RecurringItemService(db)
        }

        afterTest {
            transaction(db) {
                Comments.deleteAll()
                ListActivities.deleteAll()
                PinnedLists.deleteAll()
                ItemHistories.deleteAll()
                RecurringItems.deleteAll()
                ListShares.deleteAll()
                ListItems.deleteAll()
                ShoppingLists.deleteAll()
                HouseholdMemberships.deleteAll()
                Households.deleteAll()
                RefreshTokens.deleteAll()
                Accounts.deleteAll()
            }
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
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

        fun configureAuthentication(app: Application) {
            app.install(Authentication) {
                jwt("auth-jwt") {
                    realm = authConfig.jwt.realm
                    verifier(
                        JWT
                            .require(Algorithm.HMAC256(authConfig.jwt.secret))
                            .withIssuer(authConfig.jwt.issuer)
                            .withAudience(authConfig.jwt.audience)
                            .build(),
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
        }

        fun testModule(app: Application) {
            configureContentNegotiation(app)
            configureAuthentication(app)
            app.routing {
                authRoutes(authConfig, accountService, jwtService, refreshTokenService, tokenBlacklistService)
                shoppingListRoutes(
                    shoppingListService,
                    listItemService,
                    householdService,
                    listShareService,
                    pinnedListService,
                    eventBroadcaster,
                    activityService,
                    itemHistoryService,
                    recurringItemService,
                )
            }
        }

        test("GET /lists returns empty list initially") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                val response =
                    client.get("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                json.size shouldBe 0
            }
        }

        test("POST /lists creates a new list") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                val response =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Groceries","isPersonal":false}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "Groceries"
                json["isOwner"]?.jsonPrimitive?.content shouldBe "true"
                json["isPersonal"]?.jsonPrimitive?.content shouldBe "false"
            }
        }

        test("GET /lists/:id returns list with items") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"My List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Get the list
                val response =
                    client.get("/lists/$listId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "My List"
                json["isOwner"]?.jsonPrimitive?.content shouldBe "true"
                json["items"]?.jsonArray?.size shouldBe 0
            }
        }

        test("DELETE /lists/:id requires owner (returns 403 for non-owner)") {
            testApplication {
                application { testModule(this) }

                val ownerToken = registerAndGetToken(client, "owner@example.com")
                val otherToken = registerAndGetToken(client, "other@example.com")

                // Create a list as owner
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Owner's List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Try to delete as non-owner
                val deleteResponse =
                    client.delete("/lists/$listId") {
                        header(HttpHeaders.Authorization, "Bearer $otherToken")
                    }

                deleteResponse.status shouldBe HttpStatusCode.Forbidden
            }
        }

        test("DELETE /lists/:id succeeds for owner") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"To Delete"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Delete as owner
                val deleteResponse =
                    client.delete("/lists/$listId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                deleteResponse.status shouldBe HttpStatusCode.NoContent

                // Verify it's gone
                val getResponse =
                    client.get("/lists/$listId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("PATCH /lists/:id updates list name") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Original Name"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Update the list
                val updateResponse =
                    client.patch("/lists/$listId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Updated Name","isPersonal":true}""")
                    }

                updateResponse.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "Updated Name"
                json["isPersonal"]?.jsonPrimitive?.content shouldBe "true"
            }
        }

        test("POST /lists validates name length") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Empty name
                val emptyResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":""}""")
                    }
                emptyResponse.status shouldBe HttpStatusCode.BadRequest

                // Name too long (256 chars)
                val longName = "a".repeat(256)
                val longResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"$longName"}""")
                    }
                longResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("GET /lists returns 401 without auth") {
            testApplication {
                application { testModule(this) }

                val response = client.get("/lists")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("POST /lists/:id/items adds item") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Milk", "quantity": 2}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                response.bodyAsText().contains("Milk") shouldBe true
            }
        }

        test("POST /lists/:id/items/:itemId/check toggles check") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Add an item
                val itemResponse =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Milk", "quantity": 1}""")
                    }
                val itemId =
                    Json
                        .parseToJsonElement(itemResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.post("/lists/$listId/items/$itemId/check") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText().contains("true") shouldBe true
            }
        }

        test("DELETE /lists/:id/items/:itemId removes item") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Add an item
                val itemResponse =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Milk", "quantity": 1}""")
                    }
                val itemId =
                    Json
                        .parseToJsonElement(itemResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.delete("/lists/$listId/items/$itemId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.NoContent
            }
        }

        test("PATCH /lists/:id/items/:itemId updates item") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Add an item
                val itemResponse =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Milk", "quantity": 1}""")
                    }
                val itemId =
                    Json
                        .parseToJsonElement(itemResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.patch("/lists/$listId/items/$itemId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Eggs", "quantity": 12, "unit": "pcs"}""")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["name"]?.jsonPrimitive?.content shouldBe "Eggs"
                json["quantity"]?.jsonPrimitive?.content shouldBe "12.0"
                json["unit"]?.jsonPrimitive?.content shouldBe "pcs"
            }
        }

        test("POST /lists/:id/items validates item name length") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Empty name
                val emptyResponse =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "", "quantity": 1}""")
                    }
                emptyResponse.status shouldBe HttpStatusCode.BadRequest

                // Name too long (1001 chars)
                val longName = "a".repeat(1001)
                val longResponse =
                    client.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "$longName", "quantity": 1}""")
                    }
                longResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("POST /lists/:id/shares creates user share") {
            testApplication {
                application { testModule(this) }

                val ownerToken = registerAndGetToken(client, "owner@example.com")
                val otherToken = registerAndGetToken(client, "share-target@example.com")

                // Get the other account's ID from their token
                val otherAccountId = getAccountIdFromToken(client, otherToken)

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"type": "USER", "accountId": "$otherAccountId", "permission": "READ"}""")
                    }

                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("POST /lists/:id/shares creates link share with expiration") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val response =
                    client.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"type": "LINK", "permission": "CHECK", "expirationHours": 24}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                response.bodyAsText().contains("linkToken") shouldBe true
            }
        }

        test("GET /lists/:id/shares returns shares for owner") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Create a share
                client.post("/lists/$listId/shares") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"type": "LINK", "permission": "READ", "expirationHours": 24}""")
                }

                val response =
                    client.get("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                json.size shouldBe 1
            }
        }

        test("GET /lists/:id/shares returns 403 for non-owner") {
            testApplication {
                application { testModule(this) }

                val ownerToken = registerAndGetToken(client, "owner@example.com")
                val otherToken = registerAndGetToken(client, "other@example.com")

                // Create a list as owner
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Try to get shares as non-owner
                val response =
                    client.get("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $otherToken")
                    }

                response.status shouldBe HttpStatusCode.Forbidden
            }
        }

        test("DELETE /lists/:id/shares/:shareId revokes share") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Create a share
                val shareResponse =
                    client.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"type": "LINK", "permission": "READ", "expirationHours": 24}""")
                    }
                val shareId =
                    Json
                        .parseToJsonElement(shareResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Delete the share
                val deleteResponse =
                    client.delete("/lists/$listId/shares/$shareId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                deleteResponse.status shouldBe HttpStatusCode.NoContent

                // Verify it's gone
                val getResponse =
                    client.get("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                val json = Json.parseToJsonElement(getResponse.bodyAsText()).jsonArray
                json.size shouldBe 0
            }
        }

        test("POST /lists/:id/shares returns 400 when sharing with self") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")
                val accountId = getAccountIdFromToken(client, token)

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Try to share with self
                val response =
                    client.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"type": "USER", "accountId": "$accountId", "permission": "READ"}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("POST /lists/:id/shares returns 400 for invalid permission") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                // Create a list
                val createResponse =
                    client.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                // Try to create share with invalid permission
                val response =
                    client.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"type": "LINK", "permission": "INVALID", "expirationHours": 24}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    })

private suspend fun registerAndGetToken(
    client: io.ktor.client.HttpClient,
    email: String,
): String {
    val response =
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","displayName":"Test User","password":"password123"}""")
        }
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["token"]?.jsonPrimitive?.content ?: error("No token in response")
}

private suspend fun getAccountIdFromToken(
    client: io.ktor.client.HttpClient,
    token: String,
): String {
    val response =
        client.get("/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["id"]?.jsonPrimitive?.content ?: error("No id in response")
}
