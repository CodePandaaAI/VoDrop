package com.liftley.vodrop.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Android implementation of PlatformAuth.
 * Wraps: FirebaseAuthManager, SubscriptionManager, AccessManager
 */
actual class PlatformAuth(
    private val authManager: FirebaseAuthManager,
    private val subscriptionManager: SubscriptionManager,
    private val accessManager: AccessManager,
    private val webClientId: String
) {
    // Activity reference for auth operations (weak to prevent leaks)
    private var activityRef: WeakReference<Activity>? = null

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    private val activity: Activity?
        get() = activityRef?.get()

    actual val accessState: StateFlow<AccessState>
        get() = accessManager.accessState

    actual val isPro: StateFlow<Boolean>
        get() = subscriptionManager.isPro

    actual fun initialize() {
        authManager.initialize(webClientId)
        subscriptionManager.initialize(authManager.getCurrentUserId())
    }

    actual suspend fun signIn(): Result<User> {
        val act = activity ?: return Result.failure(Exception("No activity available"))

        return authManager.signInWithGoogle(act)
            .onSuccess { user ->
                subscriptionManager.loginWithFirebaseUser(user.id)
                accessManager.onUserLoggedIn()
            }
    }

    actual suspend fun signOut() {
        // 1. Clear local state first (UI updates immediately)
        accessManager.onUserLoggedOut()

        // 2. Logout RevenueCat
        try { subscriptionManager.logout() } catch (_: Exception) {}

        // 3. Sign out Firebase
        activity?.let {
            try { authManager.signOut(it) } catch (_: Exception) {}
        }
    }

    actual suspend fun purchaseMonthly(): Boolean {
        val act = activity ?: return false
        return subscriptionManager.purchaseMonthly(act)
    }

    actual suspend fun recordUsage(durationSeconds: Long) {
        accessManager.recordTranscriptionUsage(durationSeconds)
    }

    actual suspend fun updateProStatus(isPro: Boolean) {
        accessManager.updateProStatus(isPro)
    }

    /** Initialize AccessManager (call after Koin setup) */
    suspend fun initializeAccess() {
        accessManager.initialize()
    }
}