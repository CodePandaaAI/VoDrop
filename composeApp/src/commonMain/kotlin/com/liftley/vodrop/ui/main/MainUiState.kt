package com.liftley.vodrop.ui.main

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription

/** Recording phases */
enum class RecordingPhase { IDLE, READY, LISTENING, PROCESSING }

/** Transcription modes */
enum class TranscriptionMode(
    val displayName: String,
    val emoji: String,
    val description: String  // Add this back
) {
    STANDARD("Standard", "🎤", "Cloud transcription only"),
    WITH_AI_POLISH("AI Polish", "✨", "Cloud transcription + Gemini cleanup");

    companion object { val DEFAULT = STANDARD }
}

/** Main UI state */
data class MainUiState(
    // Recording
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    val currentTranscription: String = "",
    val progressMessage: String = "",
    val error: String? = null,

    // History
    val history: List<Transcription> = emptyList(),

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val showModeSheet: Boolean = false,
    val showSettings: Boolean = false,
    val showProfileDialog: Boolean = false,
    val showUpgradeDialog: Boolean = false,
    val showLoginPrompt: Boolean = false,

    // Settings
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,
    val cleanupStyle: CleanupStyle = CleanupStyle.DEFAULT,

    // User (testing defaults)
    val isPro: Boolean = true,
    val isLoggedIn: Boolean = true,
    val userName: String = "",

    // AI improvement
    val improvingId: Long? = null
)