package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.PinnedList
import no.shoppinglist.domain.PinnedLists
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class PinnedListService(
    private val db: Database,
) {
    fun pin(
        accountId: UUID,
        listId: UUID,
    ): Boolean =
        transaction(db) {
            val account = Account.findById(accountId) ?: return@transaction false
            val list = ShoppingList.findById(listId) ?: return@transaction false

            // Check if already pinned
            val existing =
                PinnedList
                    .find {
                        (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
                    }.firstOrNull()

            if (existing != null) return@transaction true

            PinnedList.new {
                this.account = account
                this.list = list
                this.pinnedAt = Instant.now()
            }
            true
        }

    fun unpin(
        accountId: UUID,
        listId: UUID,
    ): Boolean =
        transaction(db) {
            val deleted =
                PinnedLists.deleteWhere {
                    (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
                }
            deleted > 0
        }

    fun isPinned(
        accountId: UUID,
        listId: UUID,
    ): Boolean =
        transaction(db) {
            PinnedList
                .find {
                    (PinnedLists.account eq accountId) and (PinnedLists.list eq listId)
                }.firstOrNull() != null
        }

    fun getPinnedListIds(accountId: UUID): Set<UUID> =
        transaction(db) {
            PinnedList
                .find { PinnedLists.account eq accountId }
                .map { it.list.id.value }
                .toSet()
        }
}
