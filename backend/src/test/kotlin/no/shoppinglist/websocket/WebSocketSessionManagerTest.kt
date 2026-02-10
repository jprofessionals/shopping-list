package no.shoppinglist.websocket

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.websocket.WebSocketSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID

class WebSocketSessionManagerTest :
    FunSpec({
        lateinit var manager: WebSocketSessionManager

        beforeTest {
            manager = WebSocketSessionManager()
        }

        test("addSession registers account session") {
            val accountId = UUID.randomUUID()
            val session = mockk<WebSocketSession>()

            manager.addSession(accountId, session)

            manager.getConnectedAccountCount() shouldBe 1
        }

        test("removeSession removes session and cleans up subscriptions") {
            val accountId = UUID.randomUUID()
            val listId = UUID.randomUUID()
            val session = mockk<WebSocketSession>()

            manager.addSession(accountId, session)
            manager.subscribeToList(accountId, listId)
            manager.removeSession(accountId, session)

            manager.getConnectedAccountCount() shouldBe 0
            manager.getSubscriptionCount(listId) shouldBe 0
        }

        test("subscribeToList adds list to account subscriptions") {
            val accountId = UUID.randomUUID()
            val listId = UUID.randomUUID()
            val session = mockk<WebSocketSession>()

            manager.addSession(accountId, session)
            manager.subscribeToList(accountId, listId)

            manager.getSubscribedLists(accountId) shouldContain listId
            manager.getSubscriptionCount(listId) shouldBe 1
        }

        test("unsubscribeFromList removes list from subscriptions") {
            val accountId = UUID.randomUUID()
            val listId = UUID.randomUUID()
            val session = mockk<WebSocketSession>()

            manager.addSession(accountId, session)
            manager.subscribeToList(accountId, listId)
            manager.unsubscribeFromList(accountId, listId)

            manager.getSubscribedLists(accountId).contains(listId) shouldBe false
            manager.getSubscriptionCount(listId) shouldBe 0
        }

        test("broadcastToList sends to all subscribers except excluded") {
            val account1 = UUID.randomUUID()
            val account2 = UUID.randomUUID()
            val listId = UUID.randomUUID()
            val session1 = mockk<WebSocketSession>()
            val session2 = mockk<WebSocketSession>()

            coEvery { session1.send(any<io.ktor.websocket.Frame>()) } returns Unit
            coEvery { session2.send(any<io.ktor.websocket.Frame>()) } returns Unit

            manager.addSession(account1, session1)
            manager.addSession(account2, session2)
            manager.subscribeToList(account1, listId)
            manager.subscribeToList(account2, listId)

            val event =
                ItemCheckedEvent(
                    listId = listId.toString(),
                    itemId = UUID.randomUUID().toString(),
                    isChecked = true,
                    actor = ActorInfo(account1.toString(), "User 1"),
                )

            kotlinx.coroutines.runBlocking {
                manager.broadcastToList(listId, event, excludeAccountId = account1)
            }

            // session2 should receive, session1 should not (excluded)
            coVerify(exactly = 1) { session2.send(any<io.ktor.websocket.Frame>()) }
            coVerify(exactly = 0) { session1.send(any<io.ktor.websocket.Frame>()) }
        }
    })
