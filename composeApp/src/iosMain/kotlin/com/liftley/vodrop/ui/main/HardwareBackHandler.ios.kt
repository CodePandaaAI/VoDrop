package com.liftley.vodrop.ui.main

import androidx.compose.runtime.Composable

@Composable
actual fun HardwareBackHandler(enabled: Boolean, onBack: () -> Unit) {
    onBack()
}