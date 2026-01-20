package com.liftley.vodrop.di

import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {

    // Database
    single { DatabaseDriverFactory(androidContext()) }

    // Firestore + Device
    single { FirestoreManager() }
    single { DeviceManager(androidContext()) }

    // PlatformAuth - SINGLE auth class (consolidated)
    single { PlatformAuth(androidContext(), get(), get()) }

    // Text cleanup
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }

    // HTTP Client
    single { HttpClient(OkHttp) }
}