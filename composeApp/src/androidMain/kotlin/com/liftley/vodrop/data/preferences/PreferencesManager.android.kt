package com.liftley.vodrop.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.liftley.vodrop.data.llm.CleanupStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.core.content.edit

/**
 * Android implementation of PreferencesManager using SharedPreferences.
 */
class AndroidPreferencesManager : PreferencesManager, KoinComponent {

    private val context: Context by inject()

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("vodrop_prefs", Context.MODE_PRIVATE)
    }

    private val _preferences: MutableStateFlow<UserPreferences> by lazy {
        MutableStateFlow(loadPreferences())
    }

    override val preferences: Flow<UserPreferences>
        get() = _preferences.asStateFlow()

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CLEANUP_STYLE = "cleanup_style"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    private fun loadPreferences(): UserPreferences {
        return UserPreferences(
            userName = prefs.getString(KEY_USER_NAME, "") ?: "",
            cleanupStyle = CleanupStyle.fromName(
                prefs.getString(KEY_CLEANUP_STYLE, CleanupStyle.DEFAULT.name) ?: CleanupStyle.DEFAULT.name
            ),
            hasCompletedOnboarding = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        )
    }

    override suspend fun setUserName(name: String) {
        withContext(Dispatchers.IO) {
            prefs.edit { putString(KEY_USER_NAME, name) }
            _preferences.value = _preferences.value.copy(userName = name)
        }
    }

    override suspend fun setCleanupStyle(style: CleanupStyle) {
        withContext(Dispatchers.IO) {
            prefs.edit { putString(KEY_CLEANUP_STYLE, style.name) }
            _preferences.value = _preferences.value.copy(cleanupStyle = style)
        }
    }

    override suspend fun completeOnboarding() {
        withContext(Dispatchers.IO) {
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
            _preferences.value = _preferences.value.copy(hasCompletedOnboarding = true)
        }
    }

    override suspend fun getPreferences(): UserPreferences {
        return _preferences.value
    }

    override fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }
}

// REMOVED: actual fun createPreferencesManager(): PreferencesManager = AndroidPreferencesManager()
// Using Koin DI instead