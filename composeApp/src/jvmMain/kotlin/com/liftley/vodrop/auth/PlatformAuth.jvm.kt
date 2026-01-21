package com.liftley.vodrop.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JVM/Desktop stub - No auth on desktop version
 */
actual class PlatformAuth {

    private val _accessState = MutableStateFlow(AccessState(
        isLoading = false,
        isLoggedIn = true, // Desktop always "logged in"
        isPro = true, // Desktop always Pro (no restrictions)
        freeTrialsRemaining = 999
    ))

    actual val accessState: StateFlow<AccessState> = _accessState

    actual fun initialize() {
        // No-op on desktop
    }

    actual suspend fun signIn(): Result<User> {
        return Result.success(User("desktop", null, "Desktop User", null))
    }

    actual suspend fun signOut() {
        // No-op on desktop
    }

    actual suspend fun purchaseMonthly(): Boolean = true

    actual suspend fun recordUsage(durationSeconds: Long) {
        // No-op on desktop (unlimited)
    }
}