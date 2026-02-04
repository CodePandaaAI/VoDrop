package com.liftley.vodrop.ui.main

import androidx.compose.runtime.Composable

/**
 * **Platform-Specific Back Handler**
 * 
 * Intercepts the system "Back" gesture/button.
 * 
 * - **Android:** Hooks into `OnBackPressedDispatcher`.
 * - **iOS/Desktop:** No-op (or handled by window controller).
 */
@Composable
expect fun HardwareBackHandler(enabled: Boolean, onBack: () -> Unit)