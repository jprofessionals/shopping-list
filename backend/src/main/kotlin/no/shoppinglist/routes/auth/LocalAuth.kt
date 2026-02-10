package no.shoppinglist.routes.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.shoppinglist.domain.Account
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.RefreshTokenService

internal fun Route.loginRoute(
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
) {
    post("/login") {
        val request = call.receive<LocalLoginRequest>()
        val account = accountService.findByEmail(request.email)

        if (account == null || !accountService.verifyPassword(account, request.password)) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            return@post
        }

        call.respond(createLoginResponse(account, jwtService, refreshTokenService))
    }
}

internal fun Route.registerRoute(
    accountService: AccountService,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
) {
    post("/register") {
        val request = call.receive<LocalRegisterRequest>()

        if (accountService.findByEmail(request.email) != null) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already registered"))
            return@post
        }

        val account = accountService.createLocal(request.email, request.displayName, request.password)
        call.respond(HttpStatusCode.Created, createLoginResponse(account, jwtService, refreshTokenService))
    }
}

internal fun createLoginResponse(
    account: Account,
    jwtService: JwtService,
    refreshTokenService: RefreshTokenService,
): LoginResponse {
    val token = jwtService.generateToken(account.id.value, account.email)
    val refreshToken = refreshTokenService.createToken(account.id.value)
    return LoginResponse(
        token = token,
        refreshToken = refreshToken,
        user =
            UserResponse(
                id = account.id.value.toString(),
                email = account.email,
                displayName = account.displayName,
                avatarUrl = account.avatarUrl,
            ),
    )
}
