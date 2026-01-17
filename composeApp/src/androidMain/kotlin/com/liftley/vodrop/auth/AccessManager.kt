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
 *
 * Handles: Login state, device restriction, usage tracking, Pro status
 *
 * @see FirestoreManager for Firestore operations
 * @see DeviceManager for device ID management
 * @see SubscriptionManager for RevenueCat integration
 */
class AccessManager(
    private val firestoreManager: FirestoreManager,
    private val deviceManager: DeviceManager,
    private val subscriptionManager: SubscriptionManager
) {
    private val _accessState = MutableStateFlow(AccessState())
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    /**
     * Current access state for the user.
     * 
     * v1: Simplified - no device conflict handling.
     */
    data class AccessState(
        val isLoading: Boolean = true,
        val isLoggedIn: Boolean = false,
        val isPro: Boolean = false,
        val freeTrialsRemaining: Int = 0, // Default to 0 (only set when logged in)
        val usedMinutesThisMonth: Int = 0,
        val remainingMinutesThisMonth: Int = 120
    ) {
        // REMOVED: canTranscribe - duplicate of MainUiState.canTranscribe
        // REMOVED: hasProMinutesRemaining - not used in v1 (will be used when implementing Pro minutes limit blocking)
    }

    /**
     * Initialize and load user access state.
     */
    suspend fun initialize() {
        _accessState.value = _accessState.value.copy(isLoading = true)

        val isLoggedIn = firestoreManager.getCurrentUserId() != null

        if (!isLoggedIn) {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = false,
                freeTrialsRemaining = 0 // Explicitly reset to 0 when logged out
            )
            return
        }

        val isPro = subscriptionManager.isPro.value
        val deviceId = deviceManager.getDeviceId()
        
        // v1: Simplified - allow all devices (device restriction removed for simplicity)
        // TODO: Re-add device restriction in future version if needed
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
                freeTrialsRemaining = userData.freeTrialsRemaining, // Always from Firestore
                usedMinutesThisMonth = userData.getUsedMinutes(),
                remainingMinutesThisMonth = userData.getRemainingMinutes()
            )
            Log.d(TAG, "✅ Loaded from Firestore: trials=${userData.freeTrialsRemaining}, used=${userData.getUsedMinutes()}min, Pro=$isPro")
        } else {
            // This should never happen (getOrCreateUserData always returns data)
            // But if it does, set safe defaults
            Log.e(TAG, "Failed to get or create user data - this should not happen!")
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro,
                freeTrialsRemaining = 0 // Safe default
            )
        }
    }

    // REMOVED: switchToThisDevice() - device restriction removed for v1 simplicity

    /**
     * Record transcription usage after successful transcription.
     * 
     * IMPORTANT: Always refetch from Firestore after update to ensure consistency.
     */
    suspend fun recordTranscriptionUsage(durationSeconds: Long) {
        val currentState = _accessState.value
        
        // Must be logged in to record usage
        if (!currentState.isLoggedIn) {
            Log.w(TAG, "Cannot record usage: user not logged in")
            return
        }
        
        val deviceId = deviceManager.getDeviceId()
        val isPro = currentState.isPro // Use synced state instead of reading directly

        if (isPro) {
            // Update Pro usage
            val success = firestoreManager.addUsage(durationSeconds)
            if (success) {
                Log.d(TAG, "Updated Pro usage: +${durationSeconds}s")
                // Refetch fresh data from Firestore
                loadUserData(deviceId, isPro)
            } else {
                Log.e(TAG, "Failed to update Pro usage in Firestore")
            }
        } else {
            // Decrement free trial
            val success = firestoreManager.decrementFreeTrial()
            if (success) {
                Log.d(TAG, "Decremented free trial in Firestore")
                // Refetch fresh data from Firestore to get accurate count
                loadUserData(deviceId, isPro)
            } else {
                Log.e(TAG, "Failed to decrement free trial in Firestore")
            }
        }
    }

    suspend fun onUserLoggedIn() {
        Log.d(TAG, "User logged in - initializing and fetching from Firestore...")
        initialize()
    }

    fun onUserLoggedOut() {
        // Reset all state when user logs out
        _accessState.value = AccessState(
            isLoading = false,
            isLoggedIn = false,
            freeTrialsRemaining = 0, // Explicitly reset to 0
            isPro = false
        )
        Log.d(TAG, "User logged out - state reset")
    }

    /**
     * Update Pro status (called when RevenueCat subscription changes).
     * Refreshes user data from Firestore to ensure consistency.
     */
    suspend fun updateProStatus(isPro: Boolean) {
        val currentState = _accessState.value
        if (!currentState.isLoggedIn) {
            // Not logged in, just update Pro status
            _accessState.value = currentState.copy(isPro = isPro)
            return
        }
        
        // Logged in - refresh user data with new Pro status
        val deviceId = deviceManager.getDeviceId()
        loadUserData(deviceId, isPro)
        Log.d(TAG, "Pro status updated: $isPro - refreshed user data")
    }
}