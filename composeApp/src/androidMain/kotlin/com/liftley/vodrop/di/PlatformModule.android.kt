package com.liftley.vodrop.di

import com.liftley.vodrop.auth.AccessManager
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.preferences.AndroidPreferencesManager
import com.liftley.vodrop.data.preferences.PreferencesManager
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    // Database
    single { DatabaseDriverFactory(androidContext()) }

    // Auth & Subscription
    single { FirebaseAuthManager() }
    single { SubscriptionManager(androidContext()) }

    // Firestore & Device
    single { FirestoreManager() }
    single { DeviceManager(androidContext()) }

    // Access Manager (combines all access logic)
    single { AccessManager(get(), get(), get()) }

    // Preferences
    single<PreferencesManager> { AndroidPreferencesManager() }

    // Text cleanup service (Gemini)
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }

    // HttpClient
    single { HttpClient(OkHttp) }
}