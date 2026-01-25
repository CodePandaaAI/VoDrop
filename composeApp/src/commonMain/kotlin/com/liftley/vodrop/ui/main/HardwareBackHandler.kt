package com.liftley.vodrop.ui.main

import androidx.compose.runtime.Composable

@Composable
expect fun HardwareBackHandler(enabled: Boolean, onBack: () -> Unit)