package no.shoppinglist.config

import io.ktor.server.config.ApplicationConfig

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationMinutes: Int,
)

data class GoogleAuthConfig(
    val enabled: Boolean,
    val clientId: String,
    val clientSecret: String,
    val callbackUrl: String,
)

data class LocalAuthConfig(
    val enabled: Boolean,
)

data class AuthConfig(
    val jwt: JwtConfig,
    val google: GoogleAuthConfig,
    val local: LocalAuthConfig,
) {
    companion object {
        fun fromApplicationConfig(config: ApplicationConfig): AuthConfig =
            AuthConfig(
                jwt =
                    JwtConfig(
                        secret = config.property("auth.jwt.secret").getString(),
                        issuer = config.property("auth.jwt.issuer").getString(),
                        audience = config.property("auth.jwt.audience").getString(),
                        realm = config.property("auth.jwt.realm").getString(),
                        expirationMinutes = config.property("auth.jwt.expirationMinutes").getString().toInt(),
                    ),
                google =
                    GoogleAuthConfig(
                        enabled = config.propertyOrNull("auth.google.enabled")?.getString()?.toBoolean() ?: false,
                        clientId = config.propertyOrNull("auth.google.clientId")?.getString() ?: "",
                        clientSecret = config.propertyOrNull("auth.google.clientSecret")?.getString() ?: "",
                        callbackUrl = config.propertyOrNull("auth.google.callbackUrl")?.getString() ?: "",
                    ),
                local =
                    LocalAuthConfig(
                        enabled = config.propertyOrNull("auth.local.enabled")?.getString()?.toBoolean() ?: true,
                    ),
            )
    }
}
