package no.shoppinglist.config

import no.shoppinglist.service.TokenBlacklistService
import no.shoppinglist.service.ValkeyService

/**
 * Creates a no-op TokenBlacklistService for tests where Valkey is not available.
 * The blacklist check always returns false (no tokens are blacklisted).
 */
object TestValkeyConfig {
    fun createNoOpTokenBlacklistService(): TokenBlacklistService {
        val config = ValkeyConfig(host = "localhost", port = 6379, password = "")
        val valkeyService = ValkeyService(config)
        // Don't call connect() - the service will gracefully handle being disconnected
        return TokenBlacklistService(valkeyService)
    }
}
