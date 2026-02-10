package no.shoppinglist

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class HealthRouteTest :
    FunSpec({

        test("health endpoint returns OK") {
            testApplication {
                application {
                    routing {
                        get("/health") {
                            call.respondText("OK")
                        }
                    }
                }

                val response = client.get("/health")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "OK"
            }
        }
    })
