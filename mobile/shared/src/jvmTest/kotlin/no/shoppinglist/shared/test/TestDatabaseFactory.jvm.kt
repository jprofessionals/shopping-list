package no.shoppinglist.shared.test

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import no.shoppinglist.shared.cache.ShoppingListDatabase

actual fun createTestDatabase(): ShoppingListDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    ShoppingListDatabase.Schema.create(driver)
    return ShoppingListDatabase(driver)
}
