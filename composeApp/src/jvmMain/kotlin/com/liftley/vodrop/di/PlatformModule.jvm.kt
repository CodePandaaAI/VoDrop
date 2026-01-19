package com.liftley.vodrop.di

import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.HttpClient
import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory() }
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }
    single { HttpClient() }

    // PlatformAuth stub for desktop
    single { PlatformAuth() }
}