package com.liftley.vodrop.ui.main

import com.liftley.vodrop.domain.model.Transcription

/**
 * Transcription mode - Standard or with AI Polish
 */
enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish")
}

/**
 * UI-only state for MainScreen.
 * 
 * NOTE: Recording/processing state is in AppState (from SessionManager).
 * TranscriptionMode is also in SessionManager (single source of truth).
 * This class ONLY holds UI-specific state like dialogs, history, etc.
 */
data class MainUiState(
    // History
    val history: List<Transcription> = emptyList(),

    // Dialogs & editing
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val editText: String = "",
    val isEditingPolished: Boolean = false,
    val improvingId: Long? = null,
    
    // Bottom sheets
    val showModeSheet: Boolean = false
)