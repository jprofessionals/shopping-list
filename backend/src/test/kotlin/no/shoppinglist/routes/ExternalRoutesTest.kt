package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.service.ExternalListService
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExternalRoutesTest :
    FunSpec({
        lateinit var db: org.jetbrains.exposed.sql.Database
        lateinit var externalListService: ExternalListService

        beforeSpec {
            db = TestDatabaseConfig.init()
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(
                    Accounts,
                    Households,
                    ShoppingLists,
                    ListItems,
                    ListShares,
                )
            }
            externalListService = ExternalListService(db)
        }

        afterTest {
            transaction(db) {
                ListItems.deleteAll()
                ListShares.deleteAll()
                ShoppingLists.deleteAll()
            }
        }

        afterSpec {
            TestCleanup.dropAllTables(db)
        }

        fun testModule(app: Application) {
            app.install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            app.routing {
                externalRoutes(externalListService)
            }
        }

        test("POST /external/lists creates list and returns token") {
            testApplication {
                application { testModule(this) }

                val response =
                    client.post("/external/lists") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"title": "Party supplies"}""")
                    }

                response.status shouldBe HttpStatusCode.Created
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["shareToken"]?.jsonPrimitive?.content shouldNotBe null
                json["listId"]?.jsonPrimitive?.content shouldNotBe null
                json["widgetUrl"]?.jsonPrimitive?.content shouldNotBe null
            }
        }

        test("POST /external/lists with email and items") {
            testApplication {
                application { testModule(this) }

                val response =
                    client.post("/external/lists") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                                "title": "BBQ",
                                "email": "bob@example.com",
                                "items": [{"name": "Burgers", "quantity": 10, "unit": "pcs"}]
                            }
                            """.trimIndent(),
                        )
                    }

                response.status shouldBe HttpStatusCode.Created
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                json["shareToken"]?.jsonPrimitive?.content shouldNotBe null
            }
        }

        test("POST /external/lists returns 400 for missing title") {
            testApplication {
                application { testModule(this) }

                val response =
                    client.post("/external/lists") {
                        contentType(ContentType.Application.Json)
                        setBody("""{}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("CORS headers are present on response") {
            testApplication {
                application { testModule(this) }

                val response =
                    client.post("/external/lists") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Origin, "https://meet.example.com")
                        setBody("""{"title": "Test"}""")
                    }

                response.headers[HttpHeaders.AccessControlAllowOrigin] shouldNotBe null
            }
        }
    })
