package no.shoppinglist.config

import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer

object TestDatabaseConfig {
    private val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("test_shopping_list")
            withUsername("test")
            withPassword("test")
        }

    private var database: Database? = null

    fun init(): Database {
        if (database != null) {
            return database!!
        }

        if (!container.isRunning) {
            container.start()
        }

        database =
            Database.connect(
                url = container.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = container.username,
                password = container.password,
            )

        return database!!
    }

    fun stop() {
        if (container.isRunning) {
            container.stop()
        }
        database = null
    }
}
