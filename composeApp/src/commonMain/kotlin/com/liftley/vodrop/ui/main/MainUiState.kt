package com.liftley.vodrop.ui.main

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription

/**
 * Recording flow phases - Clear, distinct states
 */
enum class RecordingPhase {
    IDLE,           // Engine not ready
    READY,          // Ready to record
    LISTENING,      // Recording audio
    PROCESSING      // Transcribing
}

/**
 * Transcription modes with detailed descriptions
 */
enum class TranscriptionMode(
    val displayName: String,
    val emoji: String,
    val description: String,
    val requiresPro: Boolean
) {
    STANDARD(
        displayName = "Standard",
        emoji = "🎤",
        description = "Fast cloud transcription without AI cleanup",
        requiresPro = false
    ),
    WITH_AI_POLISH(
        displayName = "AI Polish",
        emoji = "✨",
        description = "Cloud transcription + Gemini 3 Flash cleanup",
        requiresPro = false  // TESTING: Set to false
    );

    companion object {
        val DEFAULT = STANDARD
    }
}

/**
 * Main UI state - Single source of truth
 */
data class MainUiState(
    // Recording state
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    val currentTranscription: String = "",
    val error: String? = null,

    // Progress message for showing detailed status
    val progressMessage: String = "",

    // History
    val history: List<Transcription> = emptyList(),

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val showTranscriptionModeSheet: Boolean = false,
    val showSettings: Boolean = false,

    // Pro features - TESTING: All forced to true/enabled
    val isPro: Boolean = true,
    val isLoggedIn: Boolean = true,
    val userName: String = "Test User",
    val userEmail: String? = "test@vodrop.com",
    val userPhotoUrl: String? = null,
    val showProfileDialog: Boolean = false,
    val showUpgradeDialog: Boolean = false,
    val showLoginPrompt: Boolean = false,
    val improvingTranscriptionId: Long? = null,

    // Transcription settings
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,
    val cleanupStyle: CleanupStyle = CleanupStyle.DEFAULT,

    // Usage tracking
    val monthlyTranscriptions: Int = 0,
    val maxFreeTranscriptions: Int = 999  // TESTING: Unlimited
) {
    val canTranscribe: Boolean
        get() = true  // TESTING: Always allow

    val remainingFreeTranscriptions: Int
        get() = 999  // TESTING: Always show plenty

    // Helper for UI to determine what's happening
    val statusMessage: String
        get() = when {
            transcriptionState is TranscriptionState.Downloading ->
                "Downloading model... ${(transcriptionState.progress * 100).toInt()}%"
            transcriptionState is TranscriptionState.Initializing ->
                transcriptionState.message
            recordingPhase == RecordingPhase.LISTENING -> "Listening..."
            recordingPhase == RecordingPhase.PROCESSING -> progressMessage.ifEmpty { "Processing..." }
            recordingPhase == RecordingPhase.READY -> "Ready"
            else -> "Initializing..."
        }
}