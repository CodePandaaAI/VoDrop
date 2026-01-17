package com.liftley.vodrop.data.firestore

import kotlinx.datetime.*

/**
 * User data stored in Firebase Firestore
 * This is the source of truth for usage tracking
 */
data class UserData(
    // Free trials (starts at 3, never resets)
    val freeTrialsRemaining: Int = 3,

    // Monthly usage tracking (in seconds)
    val currentMonthUsageSeconds: Long = 0,

    // When to reset monthly usage (ISO date string: "2026-02-01")
    val usageResetDate: String = "",

    // Device restriction
    val activeDeviceId: String = "",

    // Timestamps
    val createdAt: Long = 0,
    val lastActiveAt: Long = 0
) {
    companion object {
        // Pro plan limit: 120 minutes = 7200 seconds
        const val PRO_MONTHLY_LIMIT_SECONDS = 7200L

        // Free trials count
        const val INITIAL_FREE_TRIALS = 3

        // Get current time in milliseconds (Kotlin Multiplatform compatible)
        fun currentTimeMillis(): Long {
            return Clock.System.now().toEpochMilliseconds()
        }

        // Get the 1st of next month as reset date
        fun getNextMonthResetDate(): String {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val nextMonth = if (today.monthNumber == 12) {
                LocalDate(today.year + 1, 1, 1)
            } else {
                LocalDate(today.year, today.monthNumber + 1, 1)
            }
            return nextMonth.toString() // Returns "2026-02-01" format
        }

        // Get today's date as string
        fun getTodayDate(): String {
            return Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        }

        // Create new user data
        fun createNew(deviceId: String): UserData {
            val now = currentTimeMillis()
            return UserData(
                freeTrialsRemaining = INITIAL_FREE_TRIALS,
                currentMonthUsageSeconds = 0,
                usageResetDate = getNextMonthResetDate(),
                activeDeviceId = deviceId,
                createdAt = now,
                lastActiveAt = now
            )
        }
    }

    // Check if monthly usage should be reset
    fun shouldResetMonthlyUsage(): Boolean {
        if (usageResetDate.isEmpty()) return false
        val today = Companion.getTodayDate()
        return today >= usageResetDate
    }

    // Get remaining minutes for Pro users
    fun getRemainingMinutes(): Int {
        val usedSeconds = currentMonthUsageSeconds
        val remainingSeconds = (PRO_MONTHLY_LIMIT_SECONDS - usedSeconds).coerceAtLeast(0)
        return (remainingSeconds / 60).toInt()
    }

    // Get used minutes this month
    fun getUsedMinutes(): Int {
        return (currentMonthUsageSeconds / 60).toInt()
    }

    // Check if Pro user has exceeded limit
    fun hasExceededProLimit(): Boolean {
        return currentMonthUsageSeconds >= PRO_MONTHLY_LIMIT_SECONDS
    }
}