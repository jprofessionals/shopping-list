package no.shoppinglist.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import no.shoppinglist.config.TestCleanup
import no.shoppinglist.config.TestDatabaseConfig
import no.shoppinglist.domain.*
import no.shoppinglist.service.ExternalListService
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExternalRoutesTest : FunSpec({
    lateinit var db: org.jetbrains.exposed.sql.Database
    lateinit var externalListService: ExternalListService

    beforeSpec {
        db = TestDatabaseConfig.init()
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts, Households, ShoppingLists, ListItems, ListShares,
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

    test("POST /api/external/lists creates list and returns token") {
        testApplication {
            application { testModule(this) }

            val response = client.post("/api/external/lists") {
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

    test("POST /api/external/lists with email and items") {
        testApplication {
            application { testModule(this) }

            val response = client.post("/api/external/lists") {
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

    test("POST /api/external/lists returns 400 for missing title") {
        testApplication {
            application { testModule(this) }

            val response = client.post("/api/external/lists") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("CORS headers are present on response") {
        testApplication {
            application { testModule(this) }

            val response = client.post("/api/external/lists") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Origin, "https://meet.example.com")
                setBody("""{"title": "Test"}""")
            }

            response.headers[HttpHeaders.AccessControlAllowOrigin] shouldNotBe null
        }
    }
})
