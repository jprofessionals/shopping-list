package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ItemHistories
import no.shoppinglist.domain.ItemHistory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ItemHistoryService(
    private val db: Database,
) {
    fun recordItemUsage(
        accountId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
    ) {
        transaction(db) {
            val account = Account.findById(accountId) ?: return@transaction
            val normalizedName = name.lowercase().trim()

            val existing =
                ItemHistory
                    .find {
                        (ItemHistories.account eq accountId) and (ItemHistories.name eq normalizedName)
                    }.firstOrNull()

            if (existing != null) {
                existing.useCount += 1
                existing.lastUsedAt = Instant.now()
                // Update typical values based on most recent usage
                existing.typicalQuantity = quantity
                if (unit != null) existing.typicalUnit = unit
                existing.displayName = name.trim() // Keep latest casing
            } else {
                ItemHistory.new {
                    this.account = account
                    this.name = normalizedName
                    this.displayName = name.trim()
                    this.typicalQuantity = quantity
                    this.typicalUnit = unit
                    this.useCount = 1
                    this.lastUsedAt = Instant.now()
                }
            }
        }
    }

    fun searchSuggestions(
        accountId: UUID,
        query: String,
        limit: Int = 10,
    ): List<ItemHistory> {
        if (query.isBlank()) return emptyList()

        return transaction(db) {
            val normalizedQuery = query.lowercase().trim()
            // Escape single quotes to prevent SQL injection
            val escapedQuery = normalizedQuery.replace("'", "''")

            // Use pg_trgm similarity for fuzzy matching
            // Falls back to LIKE if similarity function not available
            exec(
                """
                SELECT id FROM item_history
                WHERE account_id = '$accountId'
                AND (
                    similarity(name, '$escapedQuery') > 0.3
                    OR name LIKE '%$escapedQuery%'
                )
                ORDER BY
                    similarity(name, '$escapedQuery') * use_count DESC,
                    use_count DESC,
                    last_used_at DESC
                LIMIT $limit
                """.trimIndent(),
            ) { rs ->
                val ids = mutableListOf<UUID>()
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("id")))
                }
                ids
            }?.mapNotNull { id ->
                ItemHistory.findById(id)
            } ?: emptyList()
        }
    }
}
