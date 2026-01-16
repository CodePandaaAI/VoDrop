package com.liftley.vodrop.data.preferences

import com.liftley.vodrop.data.llm.CleanupStyle
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing user preferences
 */
interface PreferencesManager {
    val preferences: Flow<UserPreferences>

    suspend fun setUserName(name: String)
    suspend fun setCleanupStyle(style: CleanupStyle)
    suspend fun completeOnboarding()
    suspend fun getPreferences(): UserPreferences
    fun hasCompletedOnboarding(): Boolean
}

expect fun createPreferencesManager(): PreferencesManager