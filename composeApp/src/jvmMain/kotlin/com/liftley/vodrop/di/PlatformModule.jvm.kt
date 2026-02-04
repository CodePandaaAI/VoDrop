package com.liftley.vodrop.di

import org.koin.dsl.module

/**
 * JVM/Desktop-specific Koin module.
 * Only contains truly JVM-specific dependencies.
 */
val platformModule = module {
    // Database driver
    single { DatabaseDriverFactory() }
}