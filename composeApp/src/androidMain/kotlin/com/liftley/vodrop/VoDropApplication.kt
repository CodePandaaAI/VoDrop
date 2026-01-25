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

        startKoin {
            androidLogger()
            androidContext(this@VoDropApplication)
            modules(platformModule, appModule)
        }
    }
}