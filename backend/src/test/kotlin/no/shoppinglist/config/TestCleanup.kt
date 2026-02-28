package no.shoppinglist.config

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

object TestCleanup {
    fun dropAllTables(db: Database) {
        transaction(db) {
            exec("DROP SCHEMA public CASCADE")
            exec("CREATE SCHEMA public")
        }
    }
}
