package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import no.shoppinglist.config.TestValkeyContainerConfig
import no.shoppinglist.config.ValkeyConfig

class TokenBlacklistServiceTest :
    FunSpec({
        lateinit var valkeyService: ValkeyService
        lateinit var tokenBlacklistService: TokenBlacklistService

        beforeSpec {
            valkeyService = TestValkeyContainerConfig.init()
            tokenBlacklistService = TokenBlacklistService(valkeyService)
        }

        test("blacklist sets key and isBlacklisted returns true") {
            val jti = "test-jti-${System.nanoTime()}"
            tokenBlacklistService.blacklist(jti, 60)

            tokenBlacklistService.isBlacklisted(jti) shouldBe true
        }

        test("isBlacklisted returns false for non-blacklisted jti") {
            val jti = "non-existent-jti-${System.nanoTime()}"
            tokenBlacklistService.isBlacklisted(jti) shouldBe false
        }

        test("blacklisted key expires after TTL") {
            val jti = "expiring-jti-${System.nanoTime()}"
            tokenBlacklistService.blacklist(jti, 1)

            tokenBlacklistService.isBlacklisted(jti) shouldBe true
            delay(1500)
            tokenBlacklistService.isBlacklisted(jti) shouldBe false
        }

        test("isBlacklisted returns false when Valkey is disconnected") {
            val disconnectedService =
                ValkeyService(ValkeyConfig(host = "localhost", port = 6379, password = ""))
            val noOpBlacklist = TokenBlacklistService(disconnectedService)

            noOpBlacklist.isBlacklisted("any-jti") shouldBe false
        }
    })
