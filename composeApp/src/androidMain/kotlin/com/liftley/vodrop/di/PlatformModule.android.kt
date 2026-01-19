package com.liftley.vodrop.di

import com.liftley.vodrop.auth.AccessManager
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.auth.SubscriptionManager
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

    // Auth components
    single { FirebaseAuthManager() }
    single { SubscriptionManager(androidContext()) }
    single { FirestoreManager() }
    single { DeviceManager(androidContext()) }
    single { AccessManager(get(), get(), get()) }

    // PlatformAuth - unified auth interface
    single {
        PlatformAuth(
            authManager = get(),
            subscriptionManager = get(),
            accessManager = get(),
            webClientId = "808998462431-v1mec4tnrgbosfkskedeb4kouodb8qm6.apps.googleusercontent.com"
        )
    }

    // Text cleanup
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }

    // HTTP Client
    single { HttpClient(OkHttp) }
}