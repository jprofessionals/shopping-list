package no.shoppinglist.service

import org.slf4j.LoggerFactory

class TokenBlacklistService(
    private val valkeyService: ValkeyService,
) {
    private val logger = LoggerFactory.getLogger(TokenBlacklistService::class.java)

    suspend fun blacklist(
        jti: String,
        remainingSeconds: Long,
    ) {
        if (remainingSeconds <= 0) return
        val success = valkeyService.set("blacklist:$jti", "1", remainingSeconds)
        if (!success) {
            logger.warn("Failed to blacklist token jti=$jti. Token will remain valid until expiry.")
        }
    }

    suspend fun isBlacklisted(jti: String): Boolean = valkeyService.exists("blacklist:$jti")
}
