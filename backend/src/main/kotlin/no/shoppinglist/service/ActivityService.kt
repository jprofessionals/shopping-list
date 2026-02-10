package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.ActivityType
import no.shoppinglist.domain.ListActivities
import no.shoppinglist.domain.ListActivity
import no.shoppinglist.domain.ShoppingList
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ActivityService(
    private val db: Database,
) {
    fun recordActivity(
        listId: UUID,
        accountId: UUID,
        actionType: ActivityType,
        targetName: String?,
    ) {
        transaction(db) {
            val list = ShoppingList.findById(listId) ?: return@transaction
            val account = Account.findById(accountId) ?: return@transaction

            ListActivity.new {
                this.list = list
                this.account = account
                this.actionType = actionType.value
                this.targetName = targetName
                this.createdAt = Instant.now()
            }

            // Cleanup old activities (keep last 100 or 7 days)
            cleanupOldActivities(listId)
        }
    }

    fun getActivities(
        listId: UUID,
        limit: Int = 20,
    ): List<ListActivity> =
        transaction(db) {
            ListActivity
                .find { ListActivities.list eq listId }
                .orderBy(ListActivities.createdAt to SortOrder.DESC)
                .limit(limit)
                .toList()
        }

    fun getLatestActivity(listId: UUID): ListActivity? =
        transaction(db) {
            ListActivity
                .find { ListActivities.list eq listId }
                .orderBy(ListActivities.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }

    private fun cleanupOldActivities(listId: UUID) {
        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)

        // Delete activities older than 7 days
        ListActivities.deleteWhere {
            (ListActivities.list eq listId) and (ListActivities.createdAt less sevenDaysAgo)
        }

        // Keep only last 100
        val activities =
            ListActivity
                .find { ListActivities.list eq listId }
                .orderBy(ListActivities.createdAt to SortOrder.DESC)
                .toList()

        if (activities.size > 100) {
            activities.drop(100).forEach { it.delete() }
        }
    }
}
