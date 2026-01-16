package com.liftley.vodrop.ui.main

import com.liftley.vodrop.data.stt.ModelState
import com.liftley.vodrop.data.stt.WhisperModel
import com.liftley.vodrop.domain.model.Transcription

/**
 * Phases of the recording flow
 */
enum class RecordingPhase {
    IDLE,      // No model loaded
    READY,     // Model loaded, ready to record
    LISTENING, // Currently recording
    PROCESSING // Processing transcription
}

/**
 * Transcription modes:
 * - OFFLINE_ONLY: Local Whisper.cpp only, no AI cleanup
 * - OFFLINE_WITH_AI: Local Whisper.cpp + Gemini cleanup
 * - CLOUD_WITH_AI: Groq cloud Whisper + Gemini cleanup
 */
enum class TranscriptionMode(
    val displayName: String,
    val shortName: String,
    val emoji: String,
    val description: String
) {
    OFFLINE_ONLY(
        displayName = "Offline Only",
        shortName = "Offline",
        emoji = "📱",
        description = "Fast, private, works without internet"
    ),
    OFFLINE_WITH_AI(
        displayName = "Offline + AI",
        shortName = "📱+🤖",
        emoji = "📱",
        description = "Local transcription with AI polish"
    ),
    CLOUD_WITH_AI(
        displayName = "Cloud + AI",
        shortName = "Cloud",
        emoji = "☁️",
        description = "Best accuracy, requires internet"
    );

    companion object {
        val DEFAULT = OFFLINE_WITH_AI

        fun fromOrdinal(ordinal: Int): TranscriptionMode {
            return entries.getOrElse(ordinal) { DEFAULT }
        }
    }
}

/**
 * Main UI state for the application
 */
data class MainUiState(
    // Recording state
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val modelState: ModelState = ModelState.NotLoaded,
    val selectedModel: WhisperModel = WhisperModel.DEFAULT,
    val currentTranscription: String = "",
    val error: String? = null,

    // History
    val history: List<Transcription> = emptyList(),

    // Dialogs
    val showModelSelector: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val showTranscriptionModeSheet: Boolean = false,

    // Pro features
    val isPro: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val userPhotoUrl: String? = null,
    val showProfileDialog: Boolean = false,
    val showUpgradeDialog: Boolean = false,
    val showLoginPrompt: Boolean = false,
    val improvingTranscriptionId: Long? = null,

    // Transcription mode
    val transcriptionMode: TranscriptionMode = TranscriptionMode.OFFLINE_ONLY
)