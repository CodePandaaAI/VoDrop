package com.liftley.vodrop.ui.main

import com.liftley.vodrop.domain.model.Transcription

/**
 * Unified microphone state
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
    // Mic state
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
    val improvingId: Long? = null
) {
    val statusText get() = "VoDrop • Free"

    val error: String? get() = (micPhase as? MicPhase.Error)?.message
}