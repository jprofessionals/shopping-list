package no.shoppinglist.routes.auth

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ExternalListService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.TokenBlacklistService

fun Route.authRoutes(
    authConfig: AuthConfig,
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
    tokenBlacklistService: TokenBlacklistService,
    externalListService: ExternalListService? = null,
) {
    route("/auth") {
        configRoute(authConfig)
        if (authConfig.local.enabled) {
            loginRoute(accountService, jwtService, refreshTokenService, externalListService)
            registerRoute(accountService, jwtService, refreshTokenService, externalListService)
        }
        if (authConfig.google.enabled) {
            googleAuthRoutes(authConfig, accountService, jwtService, refreshTokenService, externalListService)
        }
        refreshRoute(accountService, jwtService, refreshTokenService)
        authenticatedRoutes(accountService, tokenBlacklistService, refreshTokenService)
    }
}

private fun Route.configRoute(authConfig: AuthConfig) {
    get("/config") {
        call.respond(
            AuthConfigResponse(
                googleEnabled = authConfig.google.enabled,
                localEnabled = authConfig.local.enabled,
                googleClientId = if (authConfig.google.enabled) authConfig.google.clientId else null,
            ),
        )
    }
}
