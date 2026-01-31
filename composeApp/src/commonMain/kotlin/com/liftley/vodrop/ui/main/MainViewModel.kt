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
            else -> { /* Ignore during processing */ }
        }
    }

    /** Cancel recording or processing - one function for both */
    fun onCancel() {
        sessionManager.cancelRecording()
    }

    // ---------------- Mode ----------------

    fun selectMode(mode: TranscriptionMode) {
        sessionManager.setMode(mode)
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

    fun startEdit(t: Transcription) = _uiState.update { 
        it.copy(editingTranscription = t, editText = t.text) 
    }
    fun updateEditText(text: String) = _uiState.update { it.copy(editText = text) }
    fun cancelEdit() = _uiState.update { it.copy(editingTranscription = null, editText = "") }
    fun saveEdit() {
        val t = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyRepository.updateTranscription(t.id, _uiState.value.editText)
            _uiState.update { it.copy(editingTranscription = null, editText = "") }
        }
    }

    fun onImproveWithAI(t: Transcription) {
        _uiState.update { it.copy(improvingId = t.id) }
        viewModelScope.launch {
            transcribeUseCase.improveText(t.text)?.let { 
                historyRepository.updateTranscription(t.id, it) 
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