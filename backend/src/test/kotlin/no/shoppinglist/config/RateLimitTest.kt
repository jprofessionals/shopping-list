package no.shoppinglist.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RateLimitTest :
    FunSpec({
        test("allows requests under limit") {
            val limiter = InMemoryRateLimiter(maxRequests = 3, windowSeconds = 60)
            limiter.tryAcquire("127.0.0.1") shouldBe true
            limiter.tryAcquire("127.0.0.1") shouldBe true
            limiter.tryAcquire("127.0.0.1") shouldBe true
        }

        test("blocks requests over limit") {
            val limiter = InMemoryRateLimiter(maxRequests = 2, windowSeconds = 60)
            limiter.tryAcquire("127.0.0.1") shouldBe true
            limiter.tryAcquire("127.0.0.1") shouldBe true
            limiter.tryAcquire("127.0.0.1") shouldBe false
        }

        test("different IPs have separate limits") {
            val limiter = InMemoryRateLimiter(maxRequests = 1, windowSeconds = 60)
            limiter.tryAcquire("127.0.0.1") shouldBe true
            limiter.tryAcquire("127.0.0.2") shouldBe true
            limiter.tryAcquire("127.0.0.1") shouldBe false
        }
    })
