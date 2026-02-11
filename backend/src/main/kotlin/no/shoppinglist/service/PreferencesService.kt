package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.UserPreferences
import no.shoppinglist.domain.UserPreferencesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class PreferencesData(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
    val notifyNewList: Boolean,
    val notifyItemAdded: Boolean,
    val notifyNewComment: Boolean,
)

class PreferencesService(
    private val db: Database,
) {
    fun getPreferences(accountId: UUID): PreferencesData =
        transaction(db) {
            val prefs =
                UserPreferences
                    .find { UserPreferencesTable.account eq accountId }
                    .firstOrNull()

            prefs?.let {
                PreferencesData(
                    smartParsingEnabled = it.smartParsingEnabled,
                    defaultQuantity = it.defaultQuantity,
                    theme = it.theme,
                    notifyNewList = it.notifyNewList,
                    notifyItemAdded = it.notifyItemAdded,
                    notifyNewComment = it.notifyNewComment,
                )
            } ?: PreferencesData(
                smartParsingEnabled = true,
                defaultQuantity = 1.0,
                theme = "system",
                notifyNewList = true,
                notifyItemAdded = true,
                notifyNewComment = true,
            )
        }

    fun updatePreferences(
        accountId: UUID,
        smartParsingEnabled: Boolean?,
        defaultQuantity: Double?,
        theme: String?,
        notifyNewList: Boolean?,
        notifyItemAdded: Boolean?,
        notifyNewComment: Boolean?,
    ): PreferencesData =
        transaction(db) {
            val prefs = findOrCreatePreferences(accountId)
            smartParsingEnabled?.let { prefs.smartParsingEnabled = it }
            defaultQuantity?.let { prefs.defaultQuantity = it }
            theme?.let { prefs.theme = it }
            notifyNewList?.let { prefs.notifyNewList = it }
            notifyItemAdded?.let { prefs.notifyItemAdded = it }
            notifyNewComment?.let { prefs.notifyNewComment = it }
            prefs.updatedAt = Instant.now()
            prefs.toData()
        }

    private fun findOrCreatePreferences(accountId: UUID): UserPreferences =
        UserPreferences
            .find { UserPreferencesTable.account eq accountId }
            .firstOrNull()
            ?: UserPreferences.new {
                this.account =
                    Account.findById(accountId)
                        ?: throw IllegalArgumentException("Account not found")
                this.smartParsingEnabled = true
                this.defaultQuantity = 1.0
                this.theme = "system"
                this.notifyNewList = true
                this.notifyItemAdded = true
                this.notifyNewComment = true
                this.updatedAt = Instant.now()
            }

    private fun UserPreferences.toData() =
        PreferencesData(
            smartParsingEnabled = smartParsingEnabled,
            defaultQuantity = defaultQuantity,
            theme = theme,
            notifyNewList = notifyNewList,
            notifyItemAdded = notifyItemAdded,
            notifyNewComment = notifyNewComment,
        )
}
