package com.liftley.vodrop.data.preferences

import com.liftley.vodrop.data.llm.CleanupStyle
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing user preferences.
 * Platform implementation provided via Koin DI.
 */
interface PreferencesManager {
    val preferences: Flow<UserPreferences>

    suspend fun setUserName(name: String)
    suspend fun setCleanupStyle(style: CleanupStyle)
    suspend fun completeOnboarding()
    suspend fun getPreferences(): UserPreferences
    fun hasCompletedOnboarding(): Boolean
}

// REMOVED: expect fun createPreferencesManager(): PreferencesManager
// Using Koin DI instead: single<PreferencesManager> { AndroidPreferencesManager() }