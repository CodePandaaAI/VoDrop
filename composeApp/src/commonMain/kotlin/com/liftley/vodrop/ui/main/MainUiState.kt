package com.liftley.vodrop.ui.main

import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription

enum class RecordingPhase { IDLE, READY, LISTENING, PROCESSING }

enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish");
    companion object { val DEFAULT = STANDARD }
}

data class MainUiState(
    // Loading state - prevents actions while auth is initializing
    val isLoading: Boolean = true,

    // Recording
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    val currentTranscription: String = "",
    val progressMessage: String = "",
    val error: String? = null,

    // Recording Timer & Amplitudes
    val recordingDurationSeconds: Long = 0L,
    val currentAmplitude: Float = -60f,

    // History
    val history: List<Transcription> = emptyList(),
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val editText: String = "",
    val showUpgradeDialog: Boolean = false,
    val isDrawerOpen: Boolean = false,

    // User
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 0,
    val improvingId: Long? = null
) {
    // UPDATED: Check isLoading before allowing transcription
    val canTranscribe get() = !isLoading && isLoggedIn && (isPro || freeTrialsRemaining > 0)

    val statusText get() = when {
        isLoading -> "Loading..."
        !isLoggedIn -> "Sign in to start"
        isPro -> "Pro • Unlimited"
        freeTrialsRemaining > 0 -> "$freeTrialsRemaining trials left"
        else -> "Upgrade to Pro"
    }
}