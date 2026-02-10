package no.shoppinglist.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig

class AuthConfigTest :
    FunSpec({
        test("loads JWT configuration") {
            val config =
                MapApplicationConfig().apply {
                    put("auth.jwt.secret", "test-secret")
                    put("auth.jwt.issuer", "test-issuer")
                    put("auth.jwt.audience", "test-audience")
                    put("auth.jwt.realm", "test-realm")
                    put("auth.jwt.expirationMinutes", "48")
                }

            val authConfig = AuthConfig.fromApplicationConfig(config)

            authConfig.jwt.secret shouldBe "test-secret"
            authConfig.jwt.issuer shouldBe "test-issuer"
            authConfig.jwt.audience shouldBe "test-audience"
            authConfig.jwt.realm shouldBe "test-realm"
            authConfig.jwt.expirationMinutes shouldBe 48
        }

        test("loads Google OAuth configuration") {
            val config =
                MapApplicationConfig().apply {
                    put("auth.jwt.secret", "test-secret")
                    put("auth.jwt.issuer", "test-issuer")
                    put("auth.jwt.audience", "test-audience")
                    put("auth.jwt.realm", "test-realm")
                    put("auth.jwt.expirationMinutes", "24")
                    put("auth.google.enabled", "true")
                    put("auth.google.clientId", "google-client-id")
                    put("auth.google.clientSecret", "google-secret")
                    put("auth.google.callbackUrl", "http://localhost/callback")
                }

            val authConfig = AuthConfig.fromApplicationConfig(config)

            authConfig.google.enabled shouldBe true
            authConfig.google.clientId shouldBe "google-client-id"
            authConfig.google.clientSecret shouldBe "google-secret"
            authConfig.google.callbackUrl shouldBe "http://localhost/callback"
        }

        test("loads local auth configuration") {
            val config =
                MapApplicationConfig().apply {
                    put("auth.jwt.secret", "test-secret")
                    put("auth.jwt.issuer", "test-issuer")
                    put("auth.jwt.audience", "test-audience")
                    put("auth.jwt.realm", "test-realm")
                    put("auth.jwt.expirationMinutes", "24")
                    put("auth.local.enabled", "false")
                }

            val authConfig = AuthConfig.fromApplicationConfig(config)

            authConfig.local.enabled shouldBe false
        }
    })
