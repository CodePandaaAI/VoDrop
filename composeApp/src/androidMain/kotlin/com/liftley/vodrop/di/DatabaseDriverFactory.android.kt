package com.liftley.vodrop.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.liftley.vodrop.db.VoDropDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(VoDropDatabase.Schema, context, "vodrop.db")
    }
}