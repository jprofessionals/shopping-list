package no.shoppinglist.shared.test

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import no.shoppinglist.shared.cache.ShoppingListDatabase

actual fun createTestDatabase(): ShoppingListDatabase {
    val driver = NativeSqliteDriver(ShoppingListDatabase.Schema, "test.db")
    return ShoppingListDatabase(driver)
}
