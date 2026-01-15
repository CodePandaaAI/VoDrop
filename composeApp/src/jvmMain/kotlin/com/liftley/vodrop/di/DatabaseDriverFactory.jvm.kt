package com.liftley.vodrop.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.liftley.vodrop.db.VoDropDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = File(System.getProperty("user.home"), ".vodrop/vodrop.db")
        dbPath.parentFile?.mkdirs()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")

        // Create schema if database is new
        if (!dbPath.exists() || dbPath.length() == 0L) {
            VoDropDatabase.Schema.create(driver)
        }

        return driver
    }
}