package no.shoppinglist.config

import io.ktor.server.config.ApplicationConfig

data class ValkeyConfig(
    val host: String,
    val port: Int,
    val password: String,
) {
    companion object {
        fun fromApplicationConfig(config: ApplicationConfig): ValkeyConfig =
            ValkeyConfig(
                host = config.property("valkey.host").getString(),
                port = config.property("valkey.port").getString().toInt(),
                password = config.property("valkey.password").getString(),
            )
    }
}
