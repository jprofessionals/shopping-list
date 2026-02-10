package no.shoppinglist.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import no.shoppinglist.shared.cache.ShoppingListDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(ShoppingListDatabase.Schema, "shopping_list.db")
}
