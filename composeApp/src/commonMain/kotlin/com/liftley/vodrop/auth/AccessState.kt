package com.liftley.vodrop.auth

/**
 * Current access state for the user - shared across platforms
 */
data class AccessState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 0,
    val usedMinutesThisMonth: Int = 0,
    val remainingMinutesThisMonth: Int = 120
)