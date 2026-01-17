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
    // Recording
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    val currentTranscription: String = "",
    val progressMessage: String = "",
    val error: String? = null,

    // History
    val history: List<Transcription> = emptyList(),

    // Mode
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,
    val showModeSheet: Boolean = false,

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val showUpgradeDialog: Boolean = false,

    // User
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 3,

    // AI improvement
    val improvingId: Long? = null
) {
    val canTranscribe: Boolean get() = isLoggedIn && (isPro || freeTrialsRemaining > 0)

    val statusText: String get() = when {
        !isLoggedIn -> "Sign in to start"
        isPro -> "Pro • Unlimited"
        freeTrialsRemaining > 0 -> "$freeTrialsRemaining trials left"
        else -> "Upgrade to Pro"
    }
}