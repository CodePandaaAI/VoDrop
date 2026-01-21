package com.liftley.vodrop.auth

import kotlinx.coroutines.flow.StateFlow

actual class PlatformAuth {
    actual val accessState: StateFlow<AccessState>
        get() = TODO("Not yet implemented")
    actual val isPro: StateFlow<Boolean>
        get() = TODO("Not yet implemented")

    actual fun initialize() {
    }

    actual suspend fun signIn(): Result<User> {
        TODO("Not yet implemented")
    }

    actual suspend fun signOut() {
    }

    actual suspend fun purchaseMonthly(): Boolean {
        TODO("Not yet implemented")
    }

    actual suspend fun recordUsage(durationSeconds: Long) {
    }

    actual suspend fun updateProStatus(isPro: Boolean) {
    }
}