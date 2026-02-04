package com.liftley.vodrop.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-specific Koin module.
 * Only contains truly Android-specific dependencies.
 */
val platformModule = module {
    // Database driver (requires Android context)
    single<DatabaseDriverFactory> { DatabaseDriverFactory(androidContext()) }
}