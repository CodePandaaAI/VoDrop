package com.liftley.vodrop.di

import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import com.liftley.vodrop.data.firebase.JvmFirebaseFunctionsService
import com.liftley.vodrop.data.llm.FirebaseTextCleanupService
import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.HttpClient
import org.koin.dsl.module

val platformModule = module {
    // Database
    single { DatabaseDriverFactory() }

    // Firebase Functions (HTTP-based for JVM)
    single<FirebaseFunctionsService> { JvmFirebaseFunctionsService() }

    // Text cleanup (uses Firebase)
    single<TextCleanupService> { FirebaseTextCleanupService(get()) }

    // HTTP Client
    single { HttpClient() }

    // PlatformAuth stub for desktop
    single { PlatformAuth() }
}