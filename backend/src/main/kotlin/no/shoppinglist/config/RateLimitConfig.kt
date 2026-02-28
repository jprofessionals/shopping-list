package no.shoppinglist.config

import java.util.concurrent.ConcurrentHashMap

class InMemoryRateLimiter(
    private val maxRequests: Int,
    private val windowSeconds: Long,
) {
    private val requests = ConcurrentHashMap<String, MutableList<Long>>()

    fun tryAcquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - (windowSeconds * 1000)

        val timestamps = requests.computeIfAbsent(key) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { it < windowStart }
            if (timestamps.size >= maxRequests) return false
            timestamps.add(now)
            return true
        }
    }
}
