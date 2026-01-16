package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeEngineState()
        observeRecordingStatus()
        loadHistory()
        initializeEngine()
    }

    // ═══════════ Engine ═══════════

    private fun initializeEngine() {
        viewModelScope.launch {
            try {
                sttEngine.initialize()
            } catch (e: Exception) {
                updateState { copy(error = "Init failed: ${e.message}", recordingPhase = RecordingPhase.IDLE) }
            }
        }
    }

    // ═══════════ Recording ═══════════

    fun onRecordClick() {
        when (_uiState.value.recordingPhase) {
            RecordingPhase.IDLE -> initializeEngine()
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { /* ignore */ }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                audioRecorder.startRecording()
                updateState { copy(recordingPhase = RecordingPhase.LISTENING, currentTranscription = "", error = null) }
            } catch (e: Exception) {
                updateState { copy(error = e.message) }
            }
        }
    }

    private fun stopRecording() {
        updateState { copy(recordingPhase = RecordingPhase.PROCESSING) }

        viewModelScope.launch {
            try {
                val audioData = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                if (duration < 0.5f) {
                    updateState { copy(recordingPhase = RecordingPhase.READY, error = "Recording too short") }
                    return@launch
                }

                val mode = _uiState.value.transcriptionMode
                val result = transcribeUseCase(audioData, mode) { progress ->
                    updateState { copy(progressMessage = progress) }
                }

                when (result) {
                    is TranscribeAudioUseCase.Result.Success -> {
                        updateState { copy(currentTranscription = result.text, recordingPhase = RecordingPhase.READY, progressMessage = "") }
                        historyUseCase.saveTranscription(result.text)
                    }
                    is TranscribeAudioUseCase.Result.Error -> {
                        updateState { copy(recordingPhase = RecordingPhase.READY, error = result.message, progressMessage = "") }
                    }
                }
            } catch (e: Exception) {
                updateState { copy(recordingPhase = RecordingPhase.READY, error = "Error: ${e.message}", progressMessage = "") }
            }
        }
    }

    // ═══════════ Settings ═══════════

    fun showModeSheet() = updateState { copy(showModeSheet = true) }
    fun hideModeSheet() = updateState { copy(showModeSheet = false) }
    fun showSettings() = updateState { copy(showSettings = true) }
    fun hideSettings() = updateState { copy(showSettings = false) }

    fun selectMode(mode: TranscriptionMode) {
        updateState { copy(transcriptionMode = mode, showModeSheet = false) }
    }

    fun setCleanupStyle(style: CleanupStyle) {
        updateState { copy(cleanupStyle = style) }
    }

    fun setUserName(name: String) {
        updateState { copy(userName = name) }
    }

    // ═══════════ Auth State (called from Activity) ═══════════

    fun setProStatus(isPro: Boolean) {
        updateState { copy(isPro = isPro) }
    }

    fun setUserInfo(isLoggedIn: Boolean, name: String?, email: String?, photoUrl: String?) {
        updateState {
            copy(
                isLoggedIn = isLoggedIn,
                userName = name ?: ""
            )
        }
    }

    // ═══════════ Dialogs ═══════════

    fun showProfileDialog() = updateState { copy(showProfileDialog = true) }
    fun hideProfileDialog() = updateState { copy(showProfileDialog = false) }
    fun showUpgradeDialog() = updateState { copy(showUpgradeDialog = true) }
    fun hideUpgradeDialog() = updateState { copy(showUpgradeDialog = false) }
    fun showLoginPrompt() = updateState { copy(showLoginPrompt = true) }
    fun hideLoginPrompt() = updateState { copy(showLoginPrompt = false) }

    // ═══════════ History CRUD ═══════════

    fun requestDelete(id: Long) = updateState { copy(deleteConfirmationId = id) }
    fun cancelDelete() = updateState { copy(deleteConfirmationId = null) }

    fun confirmDelete() {
        val id = _uiState.value.deleteConfirmationId ?: return
        viewModelScope.launch {
            historyUseCase.deleteTranscription(id)
            updateState { copy(deleteConfirmationId = null) }
        }
    }

    fun startEdit(t: Transcription) = updateState { copy(editingTranscription = t) }
    fun cancelEdit() = updateState { copy(editingTranscription = null) }

    fun saveEdit(newText: String) {
        val t = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyUseCase.updateTranscription(t.id, newText)
            updateState { copy(editingTranscription = null) }
        }
    }

    fun onImproveWithAI(t: Transcription) {
        if (!_uiState.value.isPro) {
            showUpgradeDialog()
            return
        }
        updateState { copy(improvingId = t.id) }
        viewModelScope.launch {
            try {
                val result = transcribeUseCase.improveText(t.text)
                if (result != null) historyUseCase.updateTranscription(t.id, result)
            } finally {
                updateState { copy(improvingId = null) }
            }
        }
    }

    fun clearError() = updateState { copy(error = null) }

    // ═══════════ Observers ═══════════

    private fun observeEngineState() {
        viewModelScope.launch {
            sttEngine.state.collect { state ->
                val phase = when (state) {
                    is TranscriptionState.Ready -> RecordingPhase.READY
                    is TranscriptionState.Transcribing -> RecordingPhase.PROCESSING
                    else -> RecordingPhase.IDLE
                }
                val error = if (state is TranscriptionState.Error) state.message else null
                updateState { copy(transcriptionState = state, recordingPhase = phase, error = error ?: this.error) }
            }
        }
    }

    private fun observeRecordingStatus() {
        viewModelScope.launch {
            audioRecorder.status.collect { status ->
                if (status is RecordingStatus.Error) {
                    updateState { copy(error = status.message, recordingPhase = RecordingPhase.READY) }
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyUseCase.getAllTranscriptions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { list -> updateState { copy(history = list) } }
        }
    }

    private inline fun updateState(block: MainUiState.() -> MainUiState) {
        _uiState.update(block)
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        sttEngine.release()
    }
}