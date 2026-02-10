package no.shoppinglist.shared.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import no.shoppinglist.shared.cache.ShoppingListDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(ShoppingListDatabase.Schema, context, "shopping_list.db")
}
