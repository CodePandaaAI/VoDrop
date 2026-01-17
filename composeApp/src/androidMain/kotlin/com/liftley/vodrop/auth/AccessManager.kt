package com.liftley.vodrop.auth

import android.util.Log
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AccessManager"

/**
 * Unified access control manager
 * Handles: Login state, device restriction, usage tracking, Pro status
 */
class AccessManager(
    private val firestoreManager: FirestoreManager,
    private val deviceManager: DeviceManager,
    private val subscriptionManager: SubscriptionManager
) {
    // Access state
    private val _accessState = MutableStateFlow(AccessState())
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    data class AccessState(
        val isLoading: Boolean = true,
        val isLoggedIn: Boolean = false,
        val isPro: Boolean = false,
        val freeTrialsRemaining: Int = 3,
        val usedMinutesThisMonth: Int = 0,
        val remainingMinutesThisMonth: Int = 120,
        val deviceConflict: Boolean = false
    ) {
        val canTranscribe: Boolean
            get() = isLoggedIn && (isPro || freeTrialsRemaining > 0)

        val hasProMinutesRemaining: Boolean
            get() = remainingMinutesThisMonth > 0
    }

    /**
     * Initialize and load user access state
     */
    suspend fun initialize() {
        _accessState.value = _accessState.value.copy(isLoading = true)

        val userId = firestoreManager.getCurrentUserId()
        val isLoggedIn = userId != null

        if (!isLoggedIn) {
            _accessState.value = AccessState(isLoading = false, isLoggedIn = false)
            return
        }

        // Check Pro status
        val isPro = subscriptionManager.isPro.value

        // Check device
        val deviceId = deviceManager.getDeviceId()
        val isActiveDevice = firestoreManager.isActiveDevice(deviceId)

        if (!isActiveDevice) {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro,
                deviceConflict = true
            )
            return
        }

        // Load user data
        loadUserData(deviceId, isPro)
    }

    /**
     * Load user data from Firestore
     */
    private suspend fun loadUserData(deviceId: String, isPro: Boolean) {
        val userData = firestoreManager.getOrCreateUserData(deviceId)

        if (userData != null) {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro,
                freeTrialsRemaining = userData.freeTrialsRemaining,
                usedMinutesThisMonth = userData.getUsedMinutes(),
                remainingMinutesThisMonth = userData.getRemainingMinutes(),
                deviceConflict = false
            )
            Log.d(TAG, "Loaded: trials=${userData.freeTrialsRemaining}, used=${userData.getUsedMinutes()}min")
        } else {
            _accessState.value = AccessState(
                isLoading = false,
                isLoggedIn = true,
                isPro = isPro
            )
        }
    }

    /**
     * Handle device switch (user chose to use this device)
     */
    suspend fun switchToThisDevice() {
        val deviceId = deviceManager.getDeviceId()
        firestoreManager.updateActiveDevice(deviceId)
        loadUserData(deviceId, _accessState.value.isPro)
    }

    /**
     * Called after successful transcription
     */
    suspend fun recordTranscriptionUsage(durationSeconds: Long) {
        val currentState = _accessState.value

        if (currentState.isPro) {
            // Track minutes for Pro users
            firestoreManager.addUsage(durationSeconds)

            // Reload to get updated counts
            val userData = firestoreManager.getUserData()
            if (userData != null) {
                _accessState.value = currentState.copy(
                    usedMinutesThisMonth = userData.getUsedMinutes(),
                    remainingMinutesThisMonth = userData.getRemainingMinutes()
                )
            }
        } else {
            // Decrement free trial
            firestoreManager.decrementFreeTrial()
            _accessState.value = currentState.copy(
                freeTrialsRemaining = (currentState.freeTrialsRemaining - 1).coerceAtLeast(0)
            )
        }
    }

    /**
     * Called when user logs in
     */
    suspend fun onUserLoggedIn() {
        initialize()
    }

    /**
     * Called when user logs out
     */
    fun onUserLoggedOut() {
        _accessState.value = AccessState(isLoading = false, isLoggedIn = false)
    }

    /**
     * Called when Pro status changes
     */
    fun updateProStatus(isPro: Boolean) {
        _accessState.value = _accessState.value.copy(isPro = isPro)
    }
}