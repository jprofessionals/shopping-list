package no.shoppinglist.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.asyncApiRoutes() {
    route("/docs/websocket") {
        get {
            val html =
                this::class.java.classLoader
                    .getResourceAsStream("asyncapi/index.html")
                    ?.bufferedReader()
                    ?.readText()
                    ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
            call.respondText(html, ContentType.Text.Html)
        }

        get("/spec") {
            val yaml =
                this::class.java.classLoader
                    .getResourceAsStream("asyncapi/websocket.yaml")
                    ?.bufferedReader()
                    ?.readText()
                    ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
            call.respondText(yaml, ContentType.parse("application/x-yaml"))
        }
    }
}
