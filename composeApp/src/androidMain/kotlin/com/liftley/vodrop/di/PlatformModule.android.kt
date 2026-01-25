package com.liftley.vodrop.di

import com.liftley.vodrop.data.firebase.AndroidFirebaseFunctionsService
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import com.liftley.vodrop.data.llm.FirebaseTextCleanupService
import com.liftley.vodrop.data.llm.TextCleanupService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-specific Koin module.
 * Hackathon version - no auth, no RevenueCat, no Firestore user data.
 */
val platformModule = module {
    // Database driver
    single<DatabaseDriverFactory> { DatabaseDriverFactory(androidContext()) }

    single<FirebaseFunctionsService> { AndroidFirebaseFunctionsService() }

    // Text cleanup service (still uses Firebase Functions)
    single<TextCleanupService> { FirebaseTextCleanupService(
        firebaseFunctions = get()
    ) }
}