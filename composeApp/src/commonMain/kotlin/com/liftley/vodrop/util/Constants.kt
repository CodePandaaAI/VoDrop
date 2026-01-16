package com.liftley.vodrop.util

/**
 * App-wide constants
 */
object Constants {
    // Recording
    const val MIN_RECORDING_DURATION_SECONDS = 0.5f
    const val MIN_TEXT_LENGTH_FOR_AI_CLEANUP = 30

    // Inactivity timeout for model unloading (5 minutes)
    const val MODEL_UNLOAD_TIMEOUT_MS = 5 * 60 * 1000L

    // UI
    const val INACTIVITY_CHECK_INTERVAL_MS = 60_000L
}