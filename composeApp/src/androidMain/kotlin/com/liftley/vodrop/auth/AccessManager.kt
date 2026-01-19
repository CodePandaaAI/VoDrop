package com.liftley.vodrop.auth

import android.util.Log
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AccessManager"

/**
 * Unified access control manager for VoDrop v1.
 */
class AccessManager(
    private val firestoreManager: FirestoreManager,
    private val deviceManager: DeviceManager,
    private val subscriptionManager: SubscriptionManager
) {
    private val _accessState = MutableStateFlow(AccessState())
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    // REMOVED: nested data class AccessState - now using shared AccessState from commonMain

    suspend fun initialize() {
        _accessState.value = _accessState.value.copy(isLoading = true)

        val isLoggedIn = firestoreManager.getCurrentUserId() != null

        if (!isLoggedIn) {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = false,
                freeTrialsRemaining = 0
            )
            return
        }

        subscriptionManager.checkProStatus()
        val isPro = subscriptionManager.isPro.value
        val deviceId = deviceManager.getDeviceId()
        loadUserData(deviceId, isPro)
    }

    private suspend fun loadUserData(deviceId: String, isPro: Boolean) {
        Log.d(TAG, "Loading user data from Firestore...")
        val userData = firestoreManager.getOrCreateUserData(deviceId)

        if (userData != null) {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro,
                freeTrialsRemaining = userData.freeTrialsRemaining,
                usedMinutesThisMonth = userData.getUsedMinutes(),
                remainingMinutesThisMonth = userData.getRemainingMinutes()
            )
            Log.d(TAG, "✅ Loaded: trials=${userData.freeTrialsRemaining}, Pro=$isPro")
        } else {
            Log.e(TAG, "Failed to get user data!")
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro,
                freeTrialsRemaining = 0
            )
        }
    }

    suspend fun recordTranscriptionUsage(durationSeconds: Long) {
        val currentState = _accessState.value
        if (!currentState.isLoggedIn) {
            Log.w(TAG, "Cannot record usage: not logged in")
            return
        }

        val deviceId = deviceManager.getDeviceId()
        val isPro = currentState.isPro

        if (isPro) {
            val success = firestoreManager.addUsage(durationSeconds)
            if (success) {
                Log.d(TAG, "Updated Pro usage: +${durationSeconds}s")
                loadUserData(deviceId, isPro)
            }
        } else {
            val success = firestoreManager.decrementFreeTrial()
            if (success) {
                Log.d(TAG, "Decremented free trial")
                loadUserData(deviceId, isPro)
            }
        }
    }

    suspend fun onUserLoggedIn() {
        Log.d(TAG, "User logged in - initializing...")
        initialize()
    }

    fun onUserLoggedOut() {
        _accessState.value = AccessState(
            isLoading = false,
            isLoggedIn = false,
            freeTrialsRemaining = 0,
            isPro = false
        )
        Log.d(TAG, "User logged out - state reset")
    }

    suspend fun updateProStatus(isPro: Boolean) {
        val currentState = _accessState.value
        if (!currentState.isLoggedIn) {
            _accessState.value = currentState.copy(isPro = isPro)
            return
        }
        val deviceId = deviceManager.getDeviceId()
        loadUserData(deviceId, isPro)
        Log.d(TAG, "Pro status updated: $isPro")
    }
}