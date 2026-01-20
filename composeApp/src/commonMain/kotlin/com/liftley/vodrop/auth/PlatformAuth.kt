package com.liftley.vodrop.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific authentication and subscription operations.
 * Single source of truth for all auth state.
 *
 * Android: Firebase + RevenueCat + Firestore
 * JVM: Stub (no auth in desktop version)
 */
expect class PlatformAuth {

    /** Current access state (login, pro, trials, usage) - SINGLE SOURCE OF TRUTH */
    val accessState: StateFlow<AccessState>

    /** Initialize auth systems (call once at app start) */
    fun initialize()

    /** Sign in with platform credential (Google on Android) */
    suspend fun signIn(): Result<User>

    /** Sign out and clear all state */
    suspend fun signOut()

    /** Purchase monthly subscription */
    suspend fun purchaseMonthly(): Boolean

    /** Record usage after transcription */
    suspend fun recordUsage(durationSeconds: Long)
}