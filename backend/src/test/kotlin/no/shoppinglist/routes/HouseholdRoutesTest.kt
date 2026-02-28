package no.shoppinglist.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.RefreshTokens
import no.shoppinglist.routes.auth.authRoutes
import no.shoppinglist.routes.household.householdRoutes
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RecurringItemService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.TokenBlacklistService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class HouseholdRoutesTest :
    FunSpec({
        lateinit var db: Database
        lateinit var accountService: AccountService
        lateinit var householdService: HouseholdService
        lateinit var jwtService: JwtService
        lateinit var authConfig: AuthConfig
        lateinit var refreshTokenService: RefreshTokenService
        lateinit var tokenBlacklistService: TokenBlacklistService
        lateinit var recurringItemService: RecurringItemService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, Households, HouseholdMemberships, Comments, RecurringItems, RefreshTokens)
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
            jwtService = JwtService(authConfig.jwt)
            refreshTokenService = RefreshTokenService(db)
            tokenBlacklistService = TestValkeyConfig.createNoOpTokenBlacklistService()
            recurringItemService = RecurringItemService(db)
        }

        afterTest {
            transaction(db) {
                Comments.deleteAll()
                RecurringItems.deleteAll()
                RefreshTokens.deleteAll()
                HouseholdMemberships.deleteAll()
                Households.deleteAll()
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
                householdRoutes(householdService, accountService, recurringItemService)
            }
        }

        test("GET /households returns empty list for new user") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                val response =
                    client.get("/households") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                json.size shouldBe 0
            }
        }

        test("POST /households creates household") {
            testApplication {
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                val response =
                    client.post("/households") {
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
                application { testModule(this) }

                val token = registerAndGetToken(client, "test@example.com")

                client.post("/households") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Home"}""")
                }

                val response =
                    client.get("/households") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                json.size shouldBe 1
            }
        }

        test("DELETE /households/:id requires owner") {
            testApplication {
                application { testModule(this) }

                val ownerToken = registerAndGetToken(client, "owner@example.com")
                val memberToken = registerAndGetToken(client, "member@example.com")

                val createResponse =
                    client.post("/households") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test"}""")
                    }
                val householdId =
                    Json
                        .parseToJsonElement(createResponse.bodyAsText())
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content

                val deleteResponse =
                    client.delete("/households/$householdId") {
                        header(HttpHeaders.Authorization, "Bearer $memberToken")
                    }

                deleteResponse.status shouldBe HttpStatusCode.Forbidden
            }
        }

        test("GET /households returns 401 without auth") {
            testApplication {
                application { testModule(this) }

                val response = client.get("/households")

                response.status shouldBe HttpStatusCode.Unauthorized
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
