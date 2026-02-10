package no.shoppinglist.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import no.shoppinglist.config.TestValkeyContainerConfig
import no.shoppinglist.config.ValkeyConfig

class ValkeyServiceTest :
    FunSpec({
        lateinit var valkeyService: ValkeyService

        beforeSpec {
            valkeyService = TestValkeyContainerConfig.init()
        }

        test("set and exists work correctly") {
            val key = "test-key-${System.nanoTime()}"
            valkeyService.set(key, "value", 60) shouldBe true
            valkeyService.exists(key) shouldBe true
        }

        test("exists returns false for non-existent key") {
            valkeyService.exists("non-existent-key-${System.nanoTime()}") shouldBe false
        }

        test("publish and subscribe deliver messages") {
            val channel = "test-channel-${System.nanoTime()}"
            val received = CompletableDeferred<String>()

            valkeyService.subscribe(channel) { _, message ->
                received.complete(message)
            }

            // Small delay to ensure subscription is active
            kotlinx.coroutines.delay(100)

            valkeyService.publish(channel, "hello")

            val message = withTimeout(5000) { received.await() }
            message shouldBe "hello"
        }

        test("operations return false when disconnected") {
            val disconnectedService =
                ValkeyService(ValkeyConfig(host = "localhost", port = 6379, password = ""))
            // Don't call connect() - service is disconnected

            disconnectedService.set("key", "value", 60) shouldBe false
            disconnectedService.exists("key") shouldBe false
            disconnectedService.publish("channel", "message") shouldBe false
        }

        test("unsubscribe stops message delivery") {
            val channel = "unsub-channel-${System.nanoTime()}"
            var messageCount = 0

            valkeyService.subscribe(channel) { _, _ ->
                messageCount++
            }

            kotlinx.coroutines.delay(100)
            valkeyService.publish(channel, "first")
            kotlinx.coroutines.delay(200)

            messageCount shouldBe 1

            valkeyService.unsubscribe(channel)
            kotlinx.coroutines.delay(100)

            valkeyService.publish(channel, "second")
            kotlinx.coroutines.delay(200)

            messageCount shouldBe 1
        }
    })
