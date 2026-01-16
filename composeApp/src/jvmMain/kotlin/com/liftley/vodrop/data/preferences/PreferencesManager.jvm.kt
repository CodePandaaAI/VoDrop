package com.liftley.vodrop.data.preferences

import com.liftley.vodrop.data.llm.CleanupStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

class JvmPreferencesManager : PreferencesManager {

    private val prefsFile: File by lazy {
        val userHome = System.getProperty("user.home")
        File(userHome, ".vodrop/preferences.properties").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
    }

    private val props = Properties().apply {
        if (prefsFile.exists()) {
            prefsFile.inputStream().use { load(it) }
        }
    }

    private val _preferences = MutableStateFlow(loadPreferences())
    override val preferences: Flow<UserPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        return UserPreferences(
            userName = props.getProperty("user_name", ""),
            cleanupStyle = CleanupStyle.fromName(
                props.getProperty("cleanup_style", CleanupStyle.DEFAULT.name)
            ),
            hasCompletedOnboarding = props.getProperty("onboarding_complete", "false").toBoolean()
        )
    }

    private fun save() {
        prefsFile.outputStream().use { props.store(it, "VoDrop Preferences") }
    }

    override suspend fun setUserName(name: String) {
        props.setProperty("user_name", name)
        save()
        _preferences.value = _preferences.value.copy(userName = name)
    }

    override suspend fun setCleanupStyle(style: CleanupStyle) {
        props.setProperty("cleanup_style", style.name)
        save()
        _preferences.value = _preferences.value.copy(cleanupStyle = style)
    }

    override suspend fun completeOnboarding() {
        props.setProperty("onboarding_complete", "true")
        save()
        _preferences.value = _preferences.value.copy(hasCompletedOnboarding = true)
    }

    override suspend fun getPreferences(): UserPreferences = _preferences.value

    override fun hasCompletedOnboarding(): Boolean {
        return props.getProperty("onboarding_complete", "false").toBoolean()
    }
}

actual fun createPreferencesManager(): PreferencesManager = JvmPreferencesManager()