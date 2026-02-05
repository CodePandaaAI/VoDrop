package com.liftley.vodrop

import android.app.Application
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VoDropApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        com.google.firebase.FirebaseApp.initializeApp(this)

        // Initialize App Check
        val firebaseAppCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            } else {
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        startKoin {
            androidLogger()
            androidContext(this@VoDropApplication)
            modules(platformModule, appModule)
        }
    }
}