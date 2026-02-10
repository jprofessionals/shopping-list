package no.shoppinglist.routes

import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingCall
import java.util.UUID

fun RoutingCall.getAccountId(): UUID? =
    principal<JWTPrincipal>()
        ?.subject
        ?.let { UUID.fromString(it) }

fun RoutingCall.uuidParam(name: String): UUID? =
    parameters[name]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
