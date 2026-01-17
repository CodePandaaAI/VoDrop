package com.liftley.vodrop.data.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreManager"
private const val COLLECTION_USERS = "users"

/**
 * Manages user data in Firebase Firestore
 */
class FirestoreManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get current user ID or null if not logged in
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Get user data from Firestore
     * Returns null if user doesn't exist
     */
    suspend fun getUserData(): UserData? {
        val userId = getCurrentUserId() ?: return null

        return try {
            val doc = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(UserData::class.java)?.let { userData ->
                    // Check if monthly reset is needed
                    if (userData.shouldResetMonthlyUsage()) {
                        val resetData = resetMonthlyUsage(userData)
                        saveUserData(resetData)
                        resetData
                    } else {
                        userData
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            null
        }
    }

    /**
     * Create new user data (first time user)
     */
    suspend fun createUserData(deviceId: String): UserData? {
        val userId = getCurrentUserId() ?: return null

        val userData = UserData.createNew(deviceId)

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .set(userData)
                .await()

            Log.d(TAG, "Created new user data for $userId")
            userData
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user data", e)
            null
        }
    }

    /**
     * Save user data (update existing)
     */
    suspend fun saveUserData(userData: UserData): Boolean {
        val userId = getCurrentUserId() ?: return false

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .set(userData, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user data", e)
            false
        }
    }

    /**
     * Decrement free trials by 1
     */
    suspend fun decrementFreeTrial(): Boolean {
        val userData = getUserData() ?: return false

        if (userData.freeTrialsRemaining <= 0) return false

        val updated = userData.copy(
            freeTrialsRemaining = userData.freeTrialsRemaining - 1,
            lastActiveAt = System.currentTimeMillis()
        )

        return saveUserData(updated)
    }

    /**
     * Add usage time (for Pro users)
     */
    suspend fun addUsage(seconds: Long): Boolean {
        val userData = getUserData() ?: return false

        val updated = userData.copy(
            currentMonthUsageSeconds = userData.currentMonthUsageSeconds + seconds,
            lastActiveAt = System.currentTimeMillis()
        )

        return saveUserData(updated)
    }

    /**
     * Update active device ID
     */
    suspend fun updateActiveDevice(deviceId: String): Boolean {
        val userData = getUserData() ?: return false

        val updated = userData.copy(
            activeDeviceId = deviceId,
            lastActiveAt = System.currentTimeMillis()
        )

        return saveUserData(updated)
    }

    /**
     * Check if current device is the active device
     */
    suspend fun isActiveDevice(deviceId: String): Boolean {
        val userData = getUserData() ?: return true // New user, allow
        return userData.activeDeviceId.isEmpty() || userData.activeDeviceId == deviceId
    }

    /**
     * Reset monthly usage (called when new month starts)
     */
    private fun resetMonthlyUsage(userData: UserData): UserData {
        Log.d(TAG, "Resetting monthly usage")
        return userData.copy(
            currentMonthUsageSeconds = 0,
            usageResetDate = UserData.getNextMonthResetDate()
        )
    }

    /**
     * Get or create user data
     */
    suspend fun getOrCreateUserData(deviceId: String): UserData? {
        return getUserData() ?: createUserData(deviceId)
    }

    companion object {
        // Helper to get next month reset date (duplicated for access)
        private fun UserData.Companion.getNextMonthResetDate(): String {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.MONTH, 1)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1
            return "$year-${month.toString().padStart(2, '0')}-01"
        }
    }
}