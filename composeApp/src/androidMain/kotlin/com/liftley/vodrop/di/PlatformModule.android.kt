package com.liftley.vodrop.di

import com.liftley.vodrop.data.llm.FirebaseTextCleanupService
import com.liftley.vodrop.data.llm.TextCleanupService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-specific Koin module.
 */
val platformModule = module {
    // Database driver
    single<DatabaseDriverFactory> { DatabaseDriverFactory(androidContext()) }

    // Text cleanup service - calls Firebase Functions directly
    single<TextCleanupService> { FirebaseTextCleanupService() }
}