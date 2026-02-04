package com.liftley.vodrop.ui.main

import com.liftley.vodrop.domain.model.Transcription

/**
 * **Transcription Operation Mode**
 * 
 * Defines the pipeline strategy:
 * - [STANDARD]: Chirp 3 only (Fast).
 * - [WITH_AI_POLISH]: Chirp 3 -> Gemini 3 Flash (High Quality).
 */
enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish")
}

/**
 * **UI-Specific State**
 * 
 * Holds state relevant *only* to the UI layer (Dialogs, Bottom Sheets, Animation triggers).
 * 
 * **Architectural Distinction:**
 * - **AppState (SessionManager):** Holds the Single Source of Truth for *Business Logic* (Recording/Processing).
 * - **MainUiState (ViewModel):** Holds ephemeral *View Logic* that doesn't affect the backend.
 */
data class MainUiState(
    // History Data
    val history: List<Transcription> = emptyList(),

    // Dialogs & Editing Overlay
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val editText: String = "",
    val isEditingPolished: Boolean = false,
    val improvingId: Long? = null,
    
    // Bottom Sheets
    val showModeSheet: Boolean = false
)