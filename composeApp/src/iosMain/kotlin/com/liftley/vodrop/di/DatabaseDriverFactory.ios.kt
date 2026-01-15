package com.liftley.vodrop.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.liftley.vodrop.db.VoDropDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(VoDropDatabase.Schema, "vodrop.db")
    }
}