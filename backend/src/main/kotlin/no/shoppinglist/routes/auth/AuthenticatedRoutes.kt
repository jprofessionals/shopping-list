package no.shoppinglist.routes.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.shoppinglist.routes.getAccountId
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.TokenBlacklistService

internal fun Route.authenticatedRoutes(
    accountService: AccountService,
    tokenBlacklistService: TokenBlacklistService,
    refreshTokenService: RefreshTokenService,
) {
    authenticate("auth-jwt") {
        meRoute(accountService)
        logoutRoute(tokenBlacklistService, refreshTokenService)
    }
}

internal fun Route.refreshRoute(
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
) {
    post("/refresh") {
        val request = call.receive<RefreshRequest>()
        val accountId = refreshTokenService.validateAndGetAccountId(request.refreshToken)

        if (accountId == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired refresh token"))
            return@post
        }

        val account = accountService.findById(accountId)
        if (account == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Account not found"))
            return@post
        }

        val newRefreshToken = refreshTokenService.rotateToken(request.refreshToken, accountId)
        if (newRefreshToken == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid refresh token"))
            return@post
        }

        val newAccessToken = jwtService.generateToken(accountId, account.email)
        call.respond(
            RefreshResponse(
                token = newAccessToken,
                refreshToken = newRefreshToken,
            ),
        )
    }
}

private fun Route.meRoute(accountService: AccountService) {
    get("/me") {
        val accountId =
            call.getAccountId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val account =
            accountService.findById(accountId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Account not found"))

        call.respond(
            UserResponse(
                id = account.id.value.toString(),
                email = account.email,
                displayName = account.displayName,
                avatarUrl = account.avatarUrl,
            ),
        )
    }
}

private fun Route.logoutRoute(
    tokenBlacklistService: TokenBlacklistService,
    refreshTokenService: RefreshTokenService,
) {
    post("/logout") {
        val principal = call.principal<JWTPrincipal>()
        val jti = principal?.getClaim("jti", String::class)
        val expiresAt = principal?.expiresAt

        // Blacklist the access token
        if (jti != null && expiresAt != null) {
            val remainingSeconds = (expiresAt.time - System.currentTimeMillis()) / 1000
            tokenBlacklistService.blacklist(jti, remainingSeconds)
        }

        // Delete the refresh token if provided
        try {
            val request = call.receive<RefreshRequest>()
            refreshTokenService.deleteByToken(request.refreshToken)
        } catch (_: Exception) {
            // Body may be empty - that's fine, just skip refresh token deletion
        }

        call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
    }
}
