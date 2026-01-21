package com.liftley.vodrop.di

import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory() }

    // Text cleanup service (Stub for iOS - implement later)
    single<TextCleanupService> { IosTextCleanupService() }

    // HttpClient for iOS
    single { HttpClient(Darwin) }
}

// Stub implementation
private class IosTextCleanupService : TextCleanupService {
    override suspend fun cleanupText(rawText: String) = Result.success(rawText)
    override fun isAvailable() = false
}