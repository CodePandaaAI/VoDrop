package com.liftley.vodrop.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun HardwareBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled) {
        onBack()
    }
}