package no.shoppinglist.websocket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.config.GoogleAuthConfig
import no.shoppinglist.config.JwtConfig
import no.shoppinglist.config.LocalAuthConfig
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
import no.shoppinglist.domain.RefreshTokens
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.routes.auth.authRoutes
import no.shoppinglist.routes.shoppinglist.shoppingListRoutes
import no.shoppinglist.routes.webSocketRoutes
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ItemHistoryService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.service.TokenBlacklistService
import no.shoppinglist.service.ValkeyService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

@Suppress("LargeClass")
class WebSocketSyncTest :
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
        lateinit var sessionManager: WebSocketSessionManager
        lateinit var broadcastService: WebSocketBroadcastService

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
            // Disconnected ValkeyService triggers local SessionManager fallback
            val valkeyService = ValkeyService(ValkeyConfig(host = "localhost", port = 6379, password = ""))
            sessionManager = WebSocketSessionManager()
            broadcastService = WebSocketBroadcastService(valkeyService, sessionManager)
            eventBroadcaster = EventBroadcaster(broadcastService)
            refreshTokenService = RefreshTokenService(db)
            tokenBlacklistService = TestValkeyConfig.createNoOpTokenBlacklistService()
        }

        afterTest {
            transaction(db) {
                Comments.deleteAll()
                ListActivities.deleteAll()
                PinnedLists.deleteAll()
                ItemHistories.deleteAll()
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
            transaction(db) {
                SchemaUtils.drop(
                    Comments,
                    ListActivities,
                    PinnedLists,
                    ItemHistories,
                    ListShares,
                    ListItems,
                    ShoppingLists,
                    HouseholdMemberships,
                    Households,
                    RefreshTokens,
                    Accounts,
                )
            }
        }

        fun Application.installTestPlugins() {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
            }
            install(Authentication) {
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

        fun Application.installTestRoutes() {
            routing {
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
                )
                webSocketRoutes(
                    authConfig.jwt,
                    sessionManager,
                    shoppingListService,
                    householdService,
                    tokenBlacklistService,
                    broadcastService,
                )
            }
        }

        fun configureTestApp(app: Application) {
            app.installTestPlugins()
            app.installTestRoutes()
        }

        test("item:added broadcast - adding item via REST sends event to WebSocket") {
            testApplication {
                application { configureTestApp(this) }

                val wsClient =
                    createClient {
                        install(io.ktor.client.plugins.websocket.WebSockets)
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                    }

                val token = registerAndGetToken(wsClient, "ws-test@example.com")

                // Create a list via REST
                val createResponse =
                    wsClient.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"WebSocket Test List"}""")
                    }
                createResponse.status shouldBe HttpStatusCode.Created
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                // Connect to WebSocket
                wsClient.webSocket("/ws?token=$token") {
                    // First frame: subscribed event with auto-subscribed lists
                    val subscribedFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val subscribedJson = Json.parseToJsonElement(subscribedFrame.readText()).jsonObject
                    subscribedJson["type"]?.jsonPrimitive?.content shouldBe "subscribed"
                    subscribedJson["listIds"]?.jsonArray?.any {
                        it.jsonPrimitive.content == listId
                    } shouldBe true

                    // Add item via REST while WebSocket is connected
                    val itemResponse =
                        wsClient.post("/lists/$listId/items") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"name":"Milk","quantity":1}""")
                        }
                    itemResponse.status shouldBe HttpStatusCode.Created
                    val itemJson = Json.parseToJsonElement(itemResponse.bodyAsText()).jsonObject
                    val itemId = itemJson["id"]?.jsonPrimitive?.content!!

                    // Receive item:added event via WebSocket
                    val eventFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val eventJson = Json.parseToJsonElement(eventFrame.readText()).jsonObject
                    eventJson["type"]?.jsonPrimitive?.content shouldBe "item:added"
                    eventJson["listId"]?.jsonPrimitive?.content shouldBe listId
                    eventJson["item"]?.jsonObject?.get("id")?.jsonPrimitive?.content shouldBe itemId
                    eventJson["item"]?.jsonObject?.get("name")?.jsonPrimitive?.content shouldBe "Milk"
                    eventJson["item"]?.jsonObject?.get("isChecked")
                        ?.jsonPrimitive
                        ?.content shouldBe "false"
                    eventJson["actor"]?.jsonObject?.get("displayName")
                        ?.jsonPrimitive
                        ?.content shouldBe "Test User"
                }
            }
        }

        test("item:checked broadcast - checking item via REST sends event to WebSocket") {
            testApplication {
                application { configureTestApp(this) }

                val wsClient =
                    createClient {
                        install(io.ktor.client.plugins.websocket.WebSockets)
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                    }

                val token = registerAndGetToken(wsClient, "ws-check@example.com")

                // Create a list and add an item
                val createResponse =
                    wsClient.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Check Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                val itemResponse =
                    wsClient.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Bread","quantity":2}""")
                    }
                val itemId =
                    Json
                        .parseToJsonElement(itemResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                // Connect to WebSocket
                wsClient.webSocket("/ws?token=$token") {
                    // Consume the subscribed event
                    withTimeout(5000) { incoming.receive() as Frame.Text }

                    // Check the item via REST
                    val checkResponse =
                        wsClient.post("/lists/$listId/items/$itemId/check") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                    checkResponse.status shouldBe HttpStatusCode.OK

                    // Receive item:checked event via WebSocket
                    val eventFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val eventJson = Json.parseToJsonElement(eventFrame.readText()).jsonObject
                    eventJson["type"]?.jsonPrimitive?.content shouldBe "item:checked"
                    eventJson["listId"]?.jsonPrimitive?.content shouldBe listId
                    eventJson["itemId"]?.jsonPrimitive?.content shouldBe itemId
                    eventJson["isChecked"]?.jsonPrimitive?.content shouldBe "true"
                    eventJson["actor"]?.jsonObject?.get("displayName")
                        ?.jsonPrimitive
                        ?.content shouldBe "Test User"
                }
            }
        }

        test("item:removed broadcast - deleting item via REST sends event to WebSocket") {
            testApplication {
                application { configureTestApp(this) }

                val wsClient =
                    createClient {
                        install(io.ktor.client.plugins.websocket.WebSockets)
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                    }

                val token = registerAndGetToken(wsClient, "ws-remove@example.com")

                // Create a list and add an item
                val createResponse =
                    wsClient.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Remove Test List"}""")
                    }
                val listId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                val itemResponse =
                    wsClient.post("/lists/$listId/items") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Eggs","quantity":12}""")
                    }
                val itemId =
                    Json
                        .parseToJsonElement(itemResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                // Connect to WebSocket
                wsClient.webSocket("/ws?token=$token") {
                    // Consume the subscribed event
                    withTimeout(5000) { incoming.receive() as Frame.Text }

                    // Delete the item via REST
                    val deleteResponse =
                        wsClient.delete("/lists/$listId/items/$itemId") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                    deleteResponse.status shouldBe HttpStatusCode.NoContent

                    // Receive item:removed event via WebSocket
                    val eventFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val eventJson = Json.parseToJsonElement(eventFrame.readText()).jsonObject
                    eventJson["type"]?.jsonPrimitive?.content shouldBe "item:removed"
                    eventJson["listId"]?.jsonPrimitive?.content shouldBe listId
                    eventJson["itemId"]?.jsonPrimitive?.content shouldBe itemId
                    eventJson["actor"]?.jsonObject?.get("displayName")
                        ?.jsonPrimitive
                        ?.content shouldBe "Test User"
                }
            }
        }

        test("cross-user sync - User A modifies shared list, User B receives event") {
            testApplication {
                application { configureTestApp(this) }

                val wsClient =
                    createClient {
                        install(io.ktor.client.plugins.websocket.WebSockets)
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                    }

                val tokenA = registerAndGetToken(wsClient, "userA@example.com", "User A")
                val tokenB = registerAndGetToken(wsClient, "userB@example.com", "User B")
                val accountBId = getAccountIdFromToken(wsClient, tokenB)

                // User A creates a household
                val householdResponse =
                    wsClient.post("/lists") {
                        header(HttpHeaders.Authorization, "Bearer $tokenA")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Shared List"}""")
                    }
                householdResponse.status shouldBe HttpStatusCode.Created
                val listId =
                    Json
                        .parseToJsonElement(householdResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content!!

                // User A shares the list with User B (WRITE permission)
                val shareResponse =
                    wsClient.post("/lists/$listId/shares") {
                        header(HttpHeaders.Authorization, "Bearer $tokenA")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"type":"USER","accountId":"$accountBId","permission":"WRITE"}""",
                        )
                    }
                shareResponse.status shouldBe HttpStatusCode.Created

                // User B connects to WebSocket (auto-subscribes to accessible lists)
                wsClient.webSocket("/ws?token=$tokenB") {
                    // First frame: subscribed event - User B should be subscribed to shared list
                    val subscribedFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val subscribedJson =
                        Json.parseToJsonElement(subscribedFrame.readText()).jsonObject
                    subscribedJson["type"]?.jsonPrimitive?.content shouldBe "subscribed"
                    subscribedJson["listIds"]?.jsonArray?.any {
                        it.jsonPrimitive.content == listId
                    } shouldBe true

                    // User A adds an item via REST
                    val itemResponse =
                        wsClient.post("/lists/$listId/items") {
                            header(HttpHeaders.Authorization, "Bearer $tokenA")
                            contentType(ContentType.Application.Json)
                            setBody("""{"name":"Shared Item","quantity":3}""")
                        }
                    itemResponse.status shouldBe HttpStatusCode.Created
                    val itemJson = Json.parseToJsonElement(itemResponse.bodyAsText()).jsonObject
                    val itemId = itemJson["id"]?.jsonPrimitive?.content!!

                    // User B receives the item:added event
                    val eventFrame = withTimeout(5000) { incoming.receive() as Frame.Text }
                    val eventJson = Json.parseToJsonElement(eventFrame.readText()).jsonObject
                    eventJson["type"]?.jsonPrimitive?.content shouldBe "item:added"
                    eventJson["listId"]?.jsonPrimitive?.content shouldBe listId
                    eventJson["item"]?.jsonObject?.get("id")
                        ?.jsonPrimitive
                        ?.content shouldBe itemId
                    eventJson["item"]?.jsonObject?.get("name")
                        ?.jsonPrimitive
                        ?.content shouldBe "Shared Item"
                    eventJson["actor"]?.jsonObject?.get("displayName")
                        ?.jsonPrimitive
                        ?.content shouldBe "User A"
                }
            }
        }
    })

private suspend fun registerAndGetToken(
    client: HttpClient,
    email: String,
    displayName: String = "Test User",
): String {
    val response =
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"email":"$email","displayName":"$displayName","password":"testpass123"}""",
            )
        }
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["token"]?.jsonPrimitive?.content ?: error("No token in response: ${response.bodyAsText()}")
}

private suspend fun getAccountIdFromToken(
    client: HttpClient,
    token: String,
): String {
    val response =
        client.get("/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["id"]?.jsonPrimitive?.content ?: error("No id in response")
}
