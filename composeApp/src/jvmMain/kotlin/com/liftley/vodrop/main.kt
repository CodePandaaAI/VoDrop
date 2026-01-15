package com.liftley.vodrop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import org.koin.core.context.GlobalContext.startKoin

fun main() = application {
    startKoin {
        modules(platformModule, appModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "VoDrop"
    ) {
        App()
    }
}