package no.shoppinglist.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.config.GoogleAuthConfig
import no.shoppinglist.config.JwtConfig
import no.shoppinglist.config.LocalAuthConfig
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.config.TestValkeyConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.RefreshTokens
import no.shoppinglist.routes.auth.authRoutes
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.TokenBlacklistService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class AuthRoutesTest :
    FunSpec({
        lateinit var db: Database
        lateinit var accountService: AccountService
        lateinit var jwtService: JwtService
        lateinit var authConfig: AuthConfig
        lateinit var refreshTokenService: RefreshTokenService
        lateinit var tokenBlacklistService: TokenBlacklistService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.create(Accounts, RefreshTokens)
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
            jwtService = JwtService(authConfig.jwt)
            refreshTokenService = RefreshTokenService(db)
            tokenBlacklistService = TestValkeyConfig.createNoOpTokenBlacklistService()
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        afterTest {
            transaction(db) {
                RefreshTokens.deleteAll()
                Accounts.deleteAll()
            }
        }

        fun installContentNegotiation(app: io.ktor.server.application.Application) {
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

        fun installAuth(app: io.ktor.server.application.Application) {
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

        fun testModule(): io.ktor.server.testing.ApplicationTestBuilder.() -> Unit =
            {
                application {
                    installContentNegotiation(this)
                    installAuth(this)
                    routing {
                        authRoutes(authConfig, accountService, jwtService, refreshTokenService, tokenBlacklistService)
                    }
                }
            }

        test("GET /auth/config returns available auth methods") {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                            },
                        )
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

                    routing {
                        authRoutes(authConfig, accountService, jwtService, refreshTokenService, tokenBlacklistService)
                    }
                }

                val response = client.get("/auth/config")

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json.containsKey("googleEnabled") shouldBe true
                json.containsKey("localEnabled") shouldBe true
            }
        }

        test("GET /auth/me returns 401 when not authenticated") {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                            },
                        )
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

                    routing {
                        authRoutes(authConfig, accountService, jwtService, refreshTokenService, tokenBlacklistService)
                    }
                }

                val response = client.get("/auth/me")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("POST /auth/register creates new account") {
            testApplication {
                testModule()()

                val response =
                    client.post("/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"newuser@example.com","displayName":"New User","password":"password123"}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json.containsKey("token") shouldBe true
                json.containsKey("user") shouldBe true
                val user = json["user"]!!.jsonObject
                user["email"].toString() shouldBe "\"newuser@example.com\""
                user["displayName"].toString() shouldBe "\"New User\""
            }
        }

        test("POST /auth/login returns token for valid credentials") {
            testApplication {
                testModule()()

                // First register a user
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"testuser@example.com","displayName":"Test User","password":"password123"}""")
                }

                // Then login
                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"testuser@example.com","password":"password123"}""")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json.containsKey("token") shouldBe true
                json.containsKey("user") shouldBe true
            }
        }

        test("POST /auth/login returns 401 for invalid credentials") {
            testApplication {
                testModule()()

                val response =
                    client.post("/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"nonexistent@example.com","password":"wrong"}""")
                    }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /auth/me returns user when authenticated") {
            testApplication {
                testModule()()

                // First register a user and get the token
                val registerBody =
                    """{"email":"authuser@example.com","displayName":"Auth User","password":"password123"}"""
                val registerResponse =
                    client.post("/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(registerBody)
                    }

                val registerJson = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
                val token = registerJson["token"].toString().trim('"')

                // Then call /auth/me with the token
                val response =
                    client.get("/auth/me") {
                        header("Authorization", "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["email"].toString() shouldBe "\"authuser@example.com\""
                json["displayName"].toString() shouldBe "\"Auth User\""
            }
        }
    })
