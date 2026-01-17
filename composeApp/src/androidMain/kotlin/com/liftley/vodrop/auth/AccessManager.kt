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
     */
    data class AccessState(
        val isLoading: Boolean = true,
        val isLoggedIn: Boolean = false,
        val isPro: Boolean = false,
        val freeTrialsRemaining: Int = 3,
        val usedMinutesThisMonth: Int = 0,
        val remainingMinutesThisMonth: Int = 120,
        val deviceConflict: Boolean = false
    ) {
        // REMOVED: canTranscribe - duplicate of MainUiState.canTranscribe

        /**
         * Whether Pro user has minutes remaining this month.
         * TODO: Use this when implementing "Pro minutes exhausted" blocking
         */
        val hasProMinutesRemaining: Boolean
            get() = remainingMinutesThisMonth > 0
    }

    /**
     * Initialize and load user access state.
     */
    suspend fun initialize() {
        _accessState.value = _accessState.value.copy(isLoading = true)

        val userId = firestoreManager.getCurrentUserId()
        val isLoggedIn = userId != null

        if (!isLoggedIn) {
            _accessState.value = AccessState(isLoading = false, isLoggedIn = false)
            return
        }

        val isPro = subscriptionManager.isPro.value
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

        loadUserData(deviceId, isPro)
    }

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
     * Handle device switch when user chooses to use this device.
     * TODO: Implement device conflict UI dialog in MainActivity that calls this
     */
    suspend fun switchToThisDevice() {
        val deviceId = deviceManager.getDeviceId()
        firestoreManager.updateActiveDevice(deviceId)
        loadUserData(deviceId, _accessState.value.isPro)
    }

    /**
     * Record transcription usage after successful transcription.
     */
    suspend fun recordTranscriptionUsage(durationSeconds: Long) {
        val currentState = _accessState.value

        if (currentState.isPro) {
            firestoreManager.addUsage(durationSeconds)
            val userData = firestoreManager.getUserData()
            if (userData != null) {
                _accessState.value = currentState.copy(
                    usedMinutesThisMonth = userData.getUsedMinutes(),
                    remainingMinutesThisMonth = userData.getRemainingMinutes()
                )
            }
        } else {
            firestoreManager.decrementFreeTrial()
            _accessState.value = currentState.copy(
                freeTrialsRemaining = (currentState.freeTrialsRemaining - 1).coerceAtLeast(0)
            )
        }
    }

    suspend fun onUserLoggedIn() {
        initialize()
    }

    fun onUserLoggedOut() {
        _accessState.value = AccessState(isLoading = false, isLoggedIn = false)
    }

    fun updateProStatus(isPro: Boolean) {
        _accessState.value = _accessState.value.copy(isPro = isPro)
    }
}