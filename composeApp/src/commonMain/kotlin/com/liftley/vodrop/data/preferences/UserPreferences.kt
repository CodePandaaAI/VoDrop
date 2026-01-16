package com.liftley.vodrop.data.preferences

import com.liftley.vodrop.data.llm.CleanupStyle

/**
 * User preferences data model
 */
data class UserPreferences(
    val userName: String = "",
    val cleanupStyle: CleanupStyle = CleanupStyle.DEFAULT,
    val hasCompletedOnboarding: Boolean = false
)