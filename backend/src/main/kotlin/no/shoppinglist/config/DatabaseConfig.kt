package no.shoppinglist.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.ApplicationEnvironment
import no.shoppinglist.domain.Accounts
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.ItemHistories
import no.shoppinglist.domain.ListActivities
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.PinnedLists
import no.shoppinglist.domain.RecurringItems
import no.shoppinglist.domain.RefreshTokens
import no.shoppinglist.domain.ShoppingLists
import no.shoppinglist.domain.UserPreferencesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    private var database: Database? = null

    fun init(environment: ApplicationEnvironment) {
        val config =
            HikariConfig().apply {
                jdbcUrl = environment.config.property("database.url").getString()
                username = environment.config.property("database.user").getString()
                password = environment.config.property("database.password").getString()
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }

        database = Database.connect(HikariDataSource(config))
        createSchema()
    }

    private fun createSchema() {
        transaction(database!!) {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts,
                Households,
                HouseholdMemberships,
                ShoppingLists,
                ListItems,
                ListShares,
                RecurringItems,
                ListActivities,
                PinnedLists,
                ItemHistories,
                UserPreferencesTable,
                Comments,
                RefreshTokens,
            )

            // Enable pg_trgm extension for fuzzy search (used by item autocomplete)
            exec("CREATE EXTENSION IF NOT EXISTS pg_trgm")
        }
    }

    fun getDatabase(): Database = database ?: throw IllegalStateException("Database not initialized")
}
