package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel is now a thin UI layer mapping SessionManager state to UI State.
 * All recording logic is handled by RecordingSessionManager.
 */
class MainViewModel(
    private val sessionManager: RecordingSessionManager,
    private val historyUseCase: ManageHistoryUseCase,
    // We still keep TranscribeUseCase for "Improve with AI" which is ad-hoc,
    // but the main recording transcription is handled by sessionManager.
    private val transcribeUseCase: TranscribeAudioUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeSessionState()
        loadHistory()
    }

    // ---------------- Recording Actions ----------------

    fun onRecordClick() {
        val s = _uiState.value
        when (s.micPhase) {
            is MicPhase.Idle -> sessionManager.startRecording()
            is MicPhase.Recording -> sessionManager.stopRecording()
            else -> { /* Ignore during processing */ }
        }
    }

    fun onCancelRecording() {
        if (_uiState.value.micPhase is MicPhase.Recording) {
            sessionManager.cancelRecording()
        }
    }

    fun cancelProcessing() {
        sessionManager.cancelRecording() // Reuse cancel for stopping processing
    }

    // ---------------- Mode ----------------

    fun selectMode(mode: TranscriptionMode) {
        sessionManager.setMode(mode) // Update manager with mode
        update { copy(transcriptionMode = mode) }
    }

    // ---------------- Dialogs ----------------

    fun clearError() {
        sessionManager.resetState()
        update { copy(micPhase = MicPhase.Idle) }
    }

    // ---------------- History ----------------
    // No changes here - purely database operations
    
    fun requestDelete(id: Long) = update { copy(deleteConfirmationId = id) }
    fun cancelDelete() = update { copy(deleteConfirmationId = null) }
    fun confirmDelete() {
        _uiState.value.deleteConfirmationId?.let { id ->
            viewModelScope.launch {
                historyUseCase.deleteTranscription(id)
                update { copy(deleteConfirmationId = null) }
            }
        }
    }

    fun startEdit(t: Transcription) = update { copy(editingTranscription = t, editText = t.text) }
    fun updateEditText(text: String) = update { copy(editText = text) }
    fun cancelEdit() = update { copy(editingTranscription = null, editText = "") }
    fun saveEdit() {
        val t = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyUseCase.updateTranscription(t.id, _uiState.value.editText)
            update { copy(editingTranscription = null, editText = "") }
        }
    }

    fun onImproveWithAI(t: Transcription) {
        update { copy(improvingId = t.id) }
        viewModelScope.launch {
            try {
                transcribeUseCase.improveText(t.text)?.let { historyUseCase.updateTranscription(t.id, it) }
            } finally {
                update { copy(improvingId = null) }
            }
        }
    }

    // ---------------- Observers ----------------

    private fun observeSessionState() = viewModelScope.launch {
        sessionManager.state.collect { state ->
            when (state) {
                is RecordingSessionManager.SessionState.Idle -> {
                    update { copy(micPhase = MicPhase.Idle, progressMessage = "") }
                }
                is RecordingSessionManager.SessionState.Recording -> {
                    // Start amplitude feedback based on state updates if needed, 
                    // or trust the Composable to just animate.
                    // Real amplitude comes from separate flow if we want it,
                    // but for now relying on state transition.
                    update { copy(micPhase = MicPhase.Recording, currentTranscription = "") }
                }
                is RecordingSessionManager.SessionState.Processing -> {
                    update { copy(micPhase = MicPhase.Processing, progressMessage = state.message) }
                }
                is RecordingSessionManager.SessionState.Success -> {
                    update { copy(currentTranscription = state.text, micPhase = MicPhase.Idle, progressMessage = "") }
                    // Transcription is already saved by Manager
                }
                is RecordingSessionManager.SessionState.Error -> {
                    update { copy(micPhase = MicPhase.Error(state.message), progressMessage = "") }
                }
            }
        }
    }

    private fun loadHistory() = viewModelScope.launch {
        historyUseCase.getAllTranscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            .collect { update { copy(history = it) } }
    }

    private inline fun update(block: MainUiState.() -> MainUiState) = _uiState.update(block)

    override fun onCleared() {
        // We do NOT stop the session manager on ViewModel clear because recording should continue in background!
        // sessionManager.release() // Do not call this
    }
}