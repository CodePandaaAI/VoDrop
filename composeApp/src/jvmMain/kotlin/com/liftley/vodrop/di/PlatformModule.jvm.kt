package com.liftley.vodrop.di

import com.liftley.vodrop.data.llm.FirebaseTextCleanupService
import com.liftley.vodrop.data.llm.TextCleanupService
import org.koin.dsl.module

/**
 * JVM/Desktop-specific Koin module.
 */
val platformModule = module {
    // Database
    single { DatabaseDriverFactory() }

    // Text cleanup - calls Firebase Functions via HTTP directly
    single<TextCleanupService> { FirebaseTextCleanupService() }
}