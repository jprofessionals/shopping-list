package no.shoppinglist.websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSocketSessionManager {
    // accountId -> list of sessions (one user can have multiple tabs/devices)
    private val sessions = ConcurrentHashMap<UUID, MutableList<WebSocketSession>>()

    // listId -> set of accountIds subscribed to that list
    private val listSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // accountId -> set of listIds they're subscribed to
    private val accountSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // householdId -> set of accountIds subscribed to that household
    private val householdSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // accountId -> set of householdIds they're subscribed to
    private val accountHouseholdSubscriptions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private val json =
        Json {
            encodeDefaults = true
            classDiscriminator = "_class"
        }

    fun addSession(
        accountId: UUID,
        session: WebSocketSession,
    ) {
        sessions.computeIfAbsent(accountId) { mutableListOf() }.add(session)
    }

    fun removeSession(
        accountId: UUID,
        session: WebSocketSession,
    ) {
        sessions.computeIfPresent(accountId) { _, sessionList ->
            sessionList.remove(session)
            if (sessionList.isEmpty()) {
                // Clean up subscriptions when user has no more sessions
                accountSubscriptions[accountId]?.forEach { listId ->
                    listSubscriptions[listId]?.remove(accountId)
                }
                accountSubscriptions.remove(accountId)
                accountHouseholdSubscriptions[accountId]?.forEach { householdId ->
                    householdSubscriptions[householdId]?.remove(accountId)
                }
                accountHouseholdSubscriptions.remove(accountId)
                null // Remove the entry
            } else {
                sessionList
            }
        }
    }

    fun subscribeToList(
        accountId: UUID,
        listId: UUID,
    ) {
        listSubscriptions.computeIfAbsent(listId) { mutableSetOf() }.add(accountId)
        accountSubscriptions.computeIfAbsent(accountId) { mutableSetOf() }.add(listId)
    }

    fun unsubscribeFromList(
        accountId: UUID,
        listId: UUID,
    ) {
        listSubscriptions[listId]?.remove(accountId)
        accountSubscriptions[accountId]?.remove(listId)
    }

    fun getSubscribedLists(accountId: UUID): Set<UUID> = accountSubscriptions[accountId]?.toSet() ?: emptySet()

    suspend fun broadcastToList(
        listId: UUID,
        event: WebSocketEvent,
        excludeAccountId: UUID? = null,
    ) {
        val subscribers = listSubscriptions[listId] ?: return
        val message = json.encodeToString(event)

        subscribers
            .filter { it != excludeAccountId }
            .flatMap { accountId -> sessions[accountId] ?: emptyList() }
            .forEach { session ->
                try {
                    session.send(message)
                } catch (_: Exception) {
                    // Session might be closed, will be cleaned up
                }
            }
    }

    suspend fun sendToAccount(
        accountId: UUID,
        event: WebSocketEvent,
    ) {
        val message = json.encodeToString(event)
        sessions[accountId]?.forEach { session ->
            try {
                session.send(message)
            } catch (_: Exception) {
                // Session might be closed
            }
        }
    }

    fun subscribeToHousehold(
        accountId: UUID,
        householdId: UUID,
    ) {
        householdSubscriptions.computeIfAbsent(householdId) { mutableSetOf() }.add(accountId)
        accountHouseholdSubscriptions.computeIfAbsent(accountId) { mutableSetOf() }.add(householdId)
    }

    fun unsubscribeFromHousehold(
        accountId: UUID,
        householdId: UUID,
    ) {
        householdSubscriptions[householdId]?.remove(accountId)
        accountHouseholdSubscriptions[accountId]?.remove(householdId)
    }

    suspend fun broadcastToHousehold(
        householdId: UUID,
        event: WebSocketEvent,
        excludeAccountId: UUID? = null,
    ) {
        val subscribers = householdSubscriptions[householdId] ?: return
        val message = json.encodeToString(event)

        subscribers
            .filter { it != excludeAccountId }
            .flatMap { accountId -> sessions[accountId] ?: emptyList() }
            .forEach { session ->
                try {
                    session.send(message)
                } catch (_: Exception) {
                    // Session might be closed, will be cleaned up
                }
            }
    }

    fun getConnectedAccountCount(): Int = sessions.size

    fun getSubscriptionCount(listId: UUID): Int = listSubscriptions[listId]?.size ?: 0
}
