package com.liftley.vodrop.di

import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.data.firebase.AndroidFirebaseFunctionsService
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import com.liftley.vodrop.data.llm.FirebaseTextCleanupService
import com.liftley.vodrop.data.llm.TextCleanupService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {

    // Database
    single { DatabaseDriverFactory(androidContext()) }

    // Firestore + Device
    single { FirestoreManager() }
    single { DeviceManager(androidContext()) }

    // Firebase Functions (NEW - replaces direct API calls)
    single<FirebaseFunctionsService> { AndroidFirebaseFunctionsService() }

    // PlatformAuth
    single { PlatformAuth(androidContext(), get(), get()) }

    // Text cleanup (uses Firebase now)
    single<TextCleanupService> { FirebaseTextCleanupService(get()) }
}