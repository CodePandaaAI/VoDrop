package com.liftley.vodrop.di

import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory() }

    // Text cleanup service (Gemini)
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }

    // HttpClient for Groq API
    single { HttpClient(OkHttp) }
}