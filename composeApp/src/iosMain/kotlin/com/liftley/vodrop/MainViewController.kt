package com.liftley.vodrop

import androidx.compose.ui.window.ComposeUIViewController
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(platformModule, appModule)
        }
    }
) {
    App()
}