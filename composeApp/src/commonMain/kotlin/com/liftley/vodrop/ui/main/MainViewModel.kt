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
 * MainViewModel - thin UI layer.
 * 
 * Exposes two state flows:
 * - appState: Recording/processing state from SessionManager (SSOT)
 * - uiState: UI-specific state (dialogs, history, etc.)
 * 
 * NO state translation - appState is directly from SessionManager.
 */
class MainViewModel(
    private val sessionManager: RecordingSessionManager,
    private val historyRepository: TranscriptionRepository,
    private val transcribeUseCase: TranscribeAudioUseCase
) : ViewModel() {

    /** 
     * Recording state - DIRECT from SessionManager (no translation)
     */
    val appState: StateFlow<AppState> = sessionManager.state

    /**
     * UI-only state (dialogs, history, etc.)
     */
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Current transcription mode - read from SessionManager
     */
    val currentMode: TranscriptionMode get() = sessionManager.currentMode

    init {
        loadHistory()
    }

    // ---------------- Recording Actions ----------------

    fun onRecordClick() {
        when (appState.value) {
            is AppState.Ready -> sessionManager.startRecording()
            is AppState.Recording -> sessionManager.stopRecording()
            is AppState.Success, is AppState.Error -> {
                // Reset and start new recording
                sessionManager.resetState()
                sessionManager.startRecording()
            }
            is AppState.Processing -> { /* Ignore during processing */ }
        }
    }

    /** Cancel recording or processing - one function for both */
    fun onCancel() {
        sessionManager.cancelRecording()
    }

    // ---------------- Mode Sheet ----------------

    fun showModeSheet() = _uiState.update { it.copy(showModeSheet = true) }
    fun hideModeSheet() = _uiState.update { it.copy(showModeSheet = false) }

    fun selectMode(mode: TranscriptionMode) {
        sessionManager.setMode(mode)
        hideModeSheet()
    }

    // ---------------- Error ----------------

    fun clearError() {
        sessionManager.resetState()
    }

    // ---------------- History ----------------

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

    /** Start editing original text */
    fun startEditOriginal(t: Transcription) = _uiState.update { 
        it.copy(editingTranscription = t, editText = t.originalText, isEditingPolished = false) 
    }

    /** Start editing polished text */
    fun startEditPolished(t: Transcription) = _uiState.update { 
        it.copy(editingTranscription = t, editText = t.polishedText ?: "", isEditingPolished = true) 
    }
    
    fun updateEditText(text: String) = _uiState.update { it.copy(editText = text) }
    fun cancelEdit() = _uiState.update { it.copy(editingTranscription = null, editText = "", isEditingPolished = false) }
    
    /** Save edit to original or polished text based on which is being edited */
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

    /** Improve/re-polish transcription with AI */
    fun onImproveWithAI(t: Transcription) {
        _uiState.update { it.copy(improvingId = t.id) }
        viewModelScope.launch {
            // Polish the original text and save to polishedText column
            transcribeUseCase.improveText(t.originalText)?.let { polished ->
                historyRepository.updatePolishedText(t.id, polished)
            }
            _uiState.update { it.copy(improvingId = null) }
        }
    }

    // ---------------- Load History ----------------

    private fun loadHistory() = viewModelScope.launch {
        historyRepository.getAllTranscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            .collect { history -> _uiState.update { it.copy(history = history) } }
    }
}