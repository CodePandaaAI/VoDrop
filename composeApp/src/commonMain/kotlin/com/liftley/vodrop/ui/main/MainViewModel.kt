package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.*
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val audioRecorder: AudioRecorder,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var transcriptionJob: Job? = null

    init {
        observeRecordingStatus()
        loadHistory()
    }

    // ---------------- Recording ----------------

    fun onRecordClick() {
        val s = _uiState.value
        when (s.micPhase) {
            is MicPhase.Idle -> startRecording()
            is MicPhase.Recording -> stopRecording()
            else -> { /* Ignore during processing */ }
        }
    }

    fun onCancelRecording() {
        if (_uiState.value.micPhase is MicPhase.Recording) {
            viewModelScope.launch {
                audioRecorder.cancelRecording()
                update { copy(micPhase = MicPhase.Idle, currentTranscription = "") }
            }
        }
    }

    fun cancelProcessing() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        update { copy(micPhase = MicPhase.Idle, progressMessage = "") }
    }

    private fun startRecording() = viewModelScope.launch {
        runCatching {
            audioRecorder.startRecording()
            update { copy(micPhase = MicPhase.Recording, currentTranscription = "") }
        }.onFailure {
            update { copy(micPhase = MicPhase.Error(it.message ?: "Failed to start")) }
        }
    }

    private fun stopRecording() {
        update { copy(micPhase = MicPhase.Processing) }
        
        // Notify Service: Processing has started (Standard STT)
        audioRecorder.notifyProcessing()

        transcriptionJob = viewModelScope.launch {
            runCatching {
                val audio = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audio)

                if (duration < 0.5f) {
                    update { copy(micPhase = MicPhase.Error("Too short")) }
                    return@launch
                }

                val result = transcribeUseCase(
                    audioData = audio,
                    mode = _uiState.value.transcriptionMode,
                    onProgress = { update { copy(progressMessage = it) } },
                    onIntermediateResult = { text -> 
                        update { copy(currentTranscription = text) }
                        
                        // If we are about to polish, notify service
                        if (_uiState.value.transcriptionMode == TranscriptionMode.WITH_AI_POLISH 
                            && text.length > 20) {
                             audioRecorder.notifyPolishing()
                        }
                    }
                )

                when (result) {
                    is TranscribeAudioUseCase.UseCaseResult.Success -> {
                        update { copy(currentTranscription = result.text, micPhase = MicPhase.Idle, progressMessage = "") }
                        historyUseCase.saveTranscription(result.text)
                        
                        // Notify Service: Final Result
                        audioRecorder.notifyResult(result.text)
                    }
                    is TranscribeAudioUseCase.UseCaseResult.Error -> {
                        update { copy(micPhase = MicPhase.Error(result.message), progressMessage = "") }
                    }
                }
            }.onFailure {
                if (it !is kotlinx.coroutines.CancellationException) {
                    update { copy(micPhase = MicPhase.Error(it.message ?: "Failed"), progressMessage = "") }
                }
            }
        }
    }

    // ---------------- Mode ----------------

    fun selectMode(mode: TranscriptionMode) {
        update { copy(transcriptionMode = mode) }
    }

    // ---------------- Dialogs ----------------

    fun clearError() = update { copy(micPhase = MicPhase.Idle) }

    // ---------------- History ----------------

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

    private fun observeRecordingStatus() = viewModelScope.launch {
        audioRecorder.status.collect {
            if (it is RecordingStatus.Error) {
                update { copy(micPhase = MicPhase.Error(it.message)) }
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
        audioRecorder.release()
    }
}