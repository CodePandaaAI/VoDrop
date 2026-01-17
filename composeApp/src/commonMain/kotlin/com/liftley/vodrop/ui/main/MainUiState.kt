package com.liftley.vodrop.ui.main

import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription

/**
 * Recording flow phases.
 *
 * Flow: IDLE -> READY -> LISTENING -> PROCESSING -> READY
 */
enum class RecordingPhase {
    /** Initial state or engine not ready */
    IDLE,
    /** Engine ready, waiting for user to tap record */
    READY,
    /** Actively recording audio */
    LISTENING,
    /** Transcribing/polishing in progress */
    PROCESSING
}

/**
 * Transcription modes available in v1.
 *
 * - STANDARD: Cloud STT only (Groq Whisper)
 * - WITH_AI_POLISH: Cloud STT + Gemini cleanup (Pro only)
 */
enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish");

    companion object {
        val DEFAULT = STANDARD
    }
}

/**
 * Main screen UI state.
 *
 * This is the single source of truth for the MainScreen UI.
 * Updated by MainViewModel via StateFlow.
 */
data class MainUiState(
    // ═══════════ RECORDING STATE ═══════════
    /** Current phase of the recording flow */
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    /** STT engine state (Ready, Transcribing, etc.) */
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    /** Result text from most recent transcription */
    val currentTranscription: String = "",
    /** Progress message during transcription (e.g., "Polishing...") */
    val progressMessage: String = "",
    /** Error message to display, null if no error */
    val error: String? = null,

    // ═══════════ HISTORY ═══════════
    /** List of saved transcriptions */
    val history: List<Transcription> = emptyList(),

    // ═══════════ MODE SELECTION ═══════════
    /** Current transcription mode (Standard or AI Polish) */
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,
    /** Whether to show mode selection dialog */
    val showModeSheet: Boolean = false,

    // ═══════════ DIALOG STATES ═══════════
    /** ID of transcription pending deletion, null if no delete in progress */
    val deleteConfirmationId: Long? = null,
    /** Transcription being edited, null if not editing */
    val editingTranscription: Transcription? = null,
    /** Whether to show upgrade dialog */
    val showUpgradeDialog: Boolean = false,

    // ═══════════ USER STATE ═══════════
    /** Whether user is logged in via Google */
    val isLoggedIn: Boolean = false,
    /** Whether user has Pro subscription (via RevenueCat) */
    val isPro: Boolean = false,
    /** Remaining free trial count (0-3). Default 0, only set when logged in from Firestore. */
    val freeTrialsRemaining: Int = 0,

    // ═══════════ AI IMPROVEMENT ═══════════
    /** ID of history item currently being improved with AI, null if none */
    val improvingId: Long? = null
) {
    /**
     * Whether user can perform a transcription.
     * Requires: logged in AND (Pro OR has trials remaining)
     */
    val canTranscribe: Boolean
        get() = isLoggedIn && (isPro || freeTrialsRemaining > 0)

    /**
     * Status text shown in top bar.
     * Displays login status, Pro status, or trials remaining.
     */
    val statusText: String
        get() = when {
            !isLoggedIn -> "Sign in to start"
            isPro -> "Pro • Unlimited"
            freeTrialsRemaining > 0 -> "$freeTrialsRemaining trials left"
            else -> "Upgrade to Pro"
        }
}