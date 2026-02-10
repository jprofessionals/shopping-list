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
                )
            } ?: PreferencesData(
                smartParsingEnabled = true,
                defaultQuantity = 1.0,
                theme = "system",
            )
        }

    fun updatePreferences(
        accountId: UUID,
        smartParsingEnabled: Boolean?,
        defaultQuantity: Double?,
        theme: String?,
    ): PreferencesData =
        transaction(db) {
            val account =
                Account.findById(accountId)
                    ?: throw IllegalArgumentException("Account not found")

            val prefs =
                UserPreferences
                    .find { UserPreferencesTable.account eq accountId }
                    .firstOrNull()
                    ?: UserPreferences.new {
                        this.account = account
                        this.smartParsingEnabled = true
                        this.defaultQuantity = 1.0
                        this.theme = "system"
                        this.updatedAt = Instant.now()
                    }

            smartParsingEnabled?.let { prefs.smartParsingEnabled = it }
            defaultQuantity?.let { prefs.defaultQuantity = it }
            theme?.let { prefs.theme = it }
            prefs.updatedAt = Instant.now()

            PreferencesData(
                smartParsingEnabled = prefs.smartParsingEnabled,
                defaultQuantity = prefs.defaultQuantity,
                theme = prefs.theme,
            )
        }
}
