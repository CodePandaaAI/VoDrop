package com.liftley.vodrop.di

import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { FirebaseAuthManager(androidContext()) }
    single { SubscriptionManager(androidContext()) }
}