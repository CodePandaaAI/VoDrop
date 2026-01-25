package com.liftley.vodrop.ui.main

import com.liftley.vodrop.domain.model.Transcription

/**
 * Unified microphone state - simple and clear
 */
sealed interface MicPhase {
    data object Idle : MicPhase
    data object Recording : MicPhase
    data object Processing : MicPhase
    data class Error(val message: String) : MicPhase
}

enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish")
}

data class MainUiState(
    // Loading state - for auth initialization
    val isLoading: Boolean = true,

    // Mic state - unified, simple
    val micPhase: MicPhase = MicPhase.Idle,

    // Transcription output
    val currentTranscription: String = "",
    val progressMessage: String = "",

    // History
    val history: List<Transcription> = emptyList(),
    val transcriptionMode: TranscriptionMode = TranscriptionMode.STANDARD,

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val editText: String = "",
    val showUpgradeDialog: Boolean = false,

    // User
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 0,
    val improvingId: Long? = null
) {
    val canTranscribe get() = !isLoading && isLoggedIn && (isPro || freeTrialsRemaining > 0)

    val statusText get() = when {
        isLoading -> "Loading..."
        !isLoggedIn -> "Sign in to start"
        isPro -> "Pro • Unlimited"
        freeTrialsRemaining > 0 -> "$freeTrialsRemaining trials left"
        else -> "Upgrade to Pro"
    }

    // Convenience for error messages
    val error: String? get() = (micPhase as? MicPhase.Error)?.message
}