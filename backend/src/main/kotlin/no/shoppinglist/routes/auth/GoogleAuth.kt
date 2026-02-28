package no.shoppinglist.routes.auth

import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.domain.Account
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ExternalListService
import no.shoppinglist.service.GoogleAuthService
import no.shoppinglist.service.GoogleUserInfo
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RefreshTokenService
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("GoogleAuth")

internal fun Route.googleAuthRoutes(
    authConfig: AuthConfig,
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
    externalListService: ExternalListService? = null,
) {
    val googleAuthService = GoogleAuthService(authConfig.google)

    googleInitiateRoute(googleAuthService)
    googleCallbackRoute(googleAuthService, accountService, jwtService, refreshTokenService, externalListService)
}

private fun Route.googleInitiateRoute(googleAuthService: GoogleAuthService) {
    get("/google") {
        val state = UUID.randomUUID().toString()
        val authUrl = googleAuthService.getAuthorizationUrl(state)
        call.respondRedirect(authUrl)
    }
}

private fun Route.googleCallbackRoute(
    googleAuthService: GoogleAuthService,
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
    externalListService: ExternalListService? = null,
) {
    get("/google/callback") {
        val code = call.request.queryParameters["code"]
        val error = call.request.queryParameters["error"]

        if (error != null) {
            call.respondRedirect("http://localhost:5173/login?error=$error")
            return@get
        }

        if (code == null) {
            call.respondRedirect("http://localhost:5173/login?error=no_code")
            return@get
        }

        val account = handleGoogleCallback(code, googleAuthService, accountService)
        if (account == null) {
            call.respondRedirect("http://localhost:5173/login?error=auth_failed")
            return@get
        }

        externalListService?.claimPendingLists(account.id.value, account.email)
        val token = jwtService.generateToken(account.id.value, account.email)
        val refreshToken = refreshTokenService.createToken(account.id.value)
        call.respondRedirect(
            "http://localhost:5173/auth/callback?token=$token&refreshToken=$refreshToken",
        )
    }
}

private suspend fun handleGoogleCallback(
    code: String,
    googleAuthService: GoogleAuthService,
    accountService: AccountService,
): Account? =
    try {
        val tokens = googleAuthService.exchangeCodeForTokens(code)
        val userInfo = googleAuthService.getUserInfo(tokens.accessToken)
        findOrCreateGoogleAccount(userInfo, accountService)
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        logger.warn("Google OAuth client request failed", e)
        null
    } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
        logger.warn("Google OAuth connection timed out", e)
        null
    } catch (e: kotlinx.serialization.SerializationException) {
        logger.warn("Failed to parse Google OAuth response", e)
        null
    }

private fun findOrCreateGoogleAccount(
    userInfo: GoogleUserInfo,
    accountService: AccountService,
): Account {
    val existingByGoogleId = accountService.findByGoogleId(userInfo.id)
    val existingByEmail = accountService.findByEmail(userInfo.email)

    return when {
        existingByGoogleId != null -> existingByGoogleId
        existingByEmail != null -> {
            accountService.linkGoogleAccount(existingByEmail, userInfo.id, userInfo.picture)
            existingByEmail
        }
        else ->
            accountService.createFromGoogle(
                googleId = userInfo.id,
                email = userInfo.email,
                displayName = userInfo.name,
                avatarUrl = userInfo.picture,
            )
    }
}
