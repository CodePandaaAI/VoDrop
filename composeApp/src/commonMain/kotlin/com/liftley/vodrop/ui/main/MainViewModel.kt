package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import com.liftley.vodrop.domain.model.AppState
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * **Main View Model (UI Layer)**
 * 
 * Acts as the connection point between the UI (Compose) and the Domain Layer.
 * 
 * **Philosophy: Thin Connector**
 * - **No State Translation:** We expose [appState] directly from [RecordingSessionManager].
 *   This prevents "telephone game" bugs where the UI state drifts from the actual service state.
 * - **Event Delegation:** UI events (clicks) are forwarded immediately to [RecordingSessionManager].
 * - **UI-Specific State:** Only keeps track of purely ephemeral UI things like "Is the dialog open?"
 *   in [uiState].
 */
class MainViewModel(
    private val sessionManager: RecordingSessionManager,
    private val historyRepository: TranscriptionRepository,
    private val transcribeUseCase: TranscribeAudioUseCase
) : ViewModel() {

    // ─────────────────────────────────────────────────────────────────────────────
    // STATE EXPOSURE
    // ─────────────────────────────────────────────────────────────────────────────

    /** 
     * The single source of truth for "What is the app doing right now?" 
     * (Ready, Recording, Processing, Success, Error).
     * Observed directly by the UI.
     */
    val appState: StateFlow<AppState> = sessionManager.state

    /**
     * UI-local state for navigation, dialogs, and text editing.
     * These do not affect the core recording logic.
     */
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Shortcut to get the current mode (Standard vs Polished) for UI toggles.
     */
    val currentMode: TranscriptionMode get() = sessionManager.currentMode

    init {
        // Start observing the DB right away
        loadHistory()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // RECORDING INTENTS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Primary action button handler.
     * Context-aware: Starts if Ready, Stops if Recording, Resets if Error/Success.
     */
    fun onRecordClick() {
        when (appState.value) {
            is AppState.Ready -> sessionManager.startRecording()
            is AppState.Recording -> sessionManager.stopRecording()
            is AppState.Success, is AppState.Error -> {
                // If we display a result/error, clicking the mic button implies "Start New"
                sessionManager.resetState()
                sessionManager.startRecording()
            }
            is AppState.Processing -> { /* Debounce: Ignore clicks while processing */ }
        }
    }

    /**
     * Universal cancel action.
     * Handled safely by the Session Manager to cleanup resources.
     */
    fun onCancel() {
        sessionManager.cancelRecording()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UI ACTIONS (Dialogs/Sheets)
    // ─────────────────────────────────────────────────────────────────────────────

    fun showModeSheet() = _uiState.update { it.copy(showModeSheet = true) }
    fun hideModeSheet() = _uiState.update { it.copy(showModeSheet = false) }

    fun selectMode(mode: TranscriptionMode) {
        sessionManager.setMode(mode)
        hideModeSheet()
    }

    fun clearError() {
        sessionManager.resetState()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HISTORY ACTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    fun requestDelete(id: Long) = _uiState.update { it.copy(deleteConfirmationId = id) }
    fun cancelDelete() = _uiState.update { it.copy(deleteConfirmationId = null) }
    
    fun confirmDelete() {
        _uiState.value.deleteConfirmationId?.let { id ->
            viewModelScope.launch {
                historyRepository.deleteTranscription(id)
                _uiState.update { it.copy(deleteConfirmationId = null) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // EDITING ACTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    /** Prepare UI for editing the Raw text */
    fun startEditOriginal(t: Transcription) = _uiState.update { 
        it.copy(editingTranscription = t, editText = t.originalText, isEditingPolished = false) 
    }

    /** Prepare UI for editing the Polished text */
    fun startEditPolished(t: Transcription) = _uiState.update { 
        it.copy(editingTranscription = t, editText = t.polishedText ?: "", isEditingPolished = true) 
    }
    
    fun updateEditText(text: String) = _uiState.update { it.copy(editText = text) }
    
    fun cancelEdit() = _uiState.update { 
        it.copy(editingTranscription = null, editText = "", isEditingPolished = false) 
    }
    
    /** 
     * Commit changes to the local database.
     * Updates the specific field (original vs polished) based on what was selected.
     */
    fun saveEdit() {
        val t = _uiState.value.editingTranscription ?: return
        val isPolished = _uiState.value.isEditingPolished
        viewModelScope.launch {
            if (isPolished) {
                historyRepository.updatePolishedText(t.id, _uiState.value.editText)
            } else {
                historyRepository.updateOriginalText(t.id, _uiState.value.editText)
            }
            _uiState.update { it.copy(editingTranscription = null, editText = "", isEditingPolished = false) }
        }
    }

    /** 
     * Manually trigger AI Improvement on an existing item.
     * Used when a user wants to "Polish" a saved raw recording later.
     */
    fun onImproveWithAI(t: Transcription) {
        _uiState.update { it.copy(improvingId = t.id) }
        
        viewModelScope.launch {
            // Re-use logic from TranscribeUseCase to keep API logic centralized
            val result = transcribeUseCase.improveText(t.originalText)
            
            if (result != null) {
                historyRepository.updatePolishedText(t.id, result)
            } else {
                // In a real app, we'd emit a specialized One-Time Event here for a Toast/Snackbar
                println("[ViewModel] AI polish failed to improve text explicitly.")
            }
            _uiState.update { it.copy(improvingId = null) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DATA LOADING
    // ─────────────────────────────────────────────────────────────────────────────

    private fun loadHistory() = viewModelScope.launch {
        historyRepository.getAllTranscriptions()
            // Keep alive for 5s on configuration changes (rotation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            .collect { history -> _uiState.update { it.copy(history = history) } }
    }
}