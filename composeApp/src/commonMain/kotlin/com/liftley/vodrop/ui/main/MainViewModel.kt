package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 *
 * Responsibilities:
 * - Recording flow control (start/stop)
 * - Transcription via cloud STT
 * - History management (CRUD)
 * - Auth state synchronization (receives from Activity)
 *
 * Note: Auth logic lives in AccessManager (Android). This ViewModel
 * just receives and displays the auth state.
 */
class MainViewModel(
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * Callback invoked after successful transcription.
     * Used by Activity to track usage in Firestore.
     * @param Long - duration in seconds
     */
    var onTranscriptionComplete: ((Long) -> Unit)? = null

    init {
        observeEngineState()
        observeRecordingStatus()
        loadHistory()
        initializeEngine()
    }

    // ═══════════════════════════════════════════════════════════════
    // RECORDING FLOW
    // ═══════════════════════════════════════════════════════════════

    /**
     * Main record button handler.
     * Behavior depends on current phase:
     * - IDLE: Initialize engine
     * - READY: Start recording
     * - LISTENING: Stop and transcribe
     * - PROCESSING: No-op
     */
    fun onRecordClick() {
        val state = _uiState.value

        // Gate: Must be logged in
        if (!state.isLoggedIn) {
            update { copy(error = "Please sign in first") }
            return
        }

        // Gate: Must have access (Pro or trials)
        if (!state.canTranscribe) {
            update { copy(showUpgradeDialog = true) }
            return
        }

        when (state.recordingPhase) {
            RecordingPhase.IDLE -> initializeEngine()
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { /* No-op while processing */ }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            try {
                sttEngine.initialize()
            } catch (e: Exception) {
                update { copy(error = e.message) }
            }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                audioRecorder.startRecording()
                update { copy(recordingPhase = RecordingPhase.LISTENING, currentTranscription = "", error = null) }
            } catch (e: Exception) {
                update { copy(error = e.message) }
            }
        }
    }

    private fun stopRecording() {
        update { copy(recordingPhase = RecordingPhase.PROCESSING) }

        viewModelScope.launch {
            try {
                val audioData = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                // Minimum recording length check
                if (duration < 0.5f) {
                    update { copy(recordingPhase = RecordingPhase.READY, error = "Too short") }
                    return@launch
                }

                // Transcribe
                val result = transcribeUseCase(audioData, _uiState.value.transcriptionMode) { msg ->
                    update { copy(progressMessage = msg) }
                }

                when (result) {
                    is TranscribeAudioUseCase.Result.Success -> {
                        update { copy(currentTranscription = result.text, recordingPhase = RecordingPhase.READY, progressMessage = "") }
                        historyUseCase.saveTranscription(result.text)
                        onTranscriptionComplete?.invoke(duration.toLong())
                    }
                    is TranscribeAudioUseCase.Result.Error -> {
                        update { copy(recordingPhase = RecordingPhase.READY, error = result.message, progressMessage = "") }
                    }
                }
            } catch (e: Exception) {
                update { copy(recordingPhase = RecordingPhase.READY, error = e.message, progressMessage = "") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE SELECTION
    // ═══════════════════════════════════════════════════════════════

    fun showModeSheet() = update { copy(showModeSheet = true) }
    fun hideModeSheet() = update { copy(showModeSheet = false) }

    fun selectMode(mode: TranscriptionMode) {
        // AI Polish requires Pro
        if (mode == TranscriptionMode.WITH_AI_POLISH && !_uiState.value.isPro) {
            update { copy(showUpgradeDialog = true) }
            return
        }
        update { copy(transcriptionMode = mode, showModeSheet = false) }
    }

    // ═══════════════════════════════════════════════════════════════
    // AUTH STATE (Synced from Activity/AccessManager)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called from Activity when auth state changes.
     * This ViewModel doesn't handle auth logic directly.
     */
    fun setAuth(isLoggedIn: Boolean, isPro: Boolean, freeTrials: Int) {
        update { copy(isLoggedIn = isLoggedIn, isPro = isPro, freeTrialsRemaining = freeTrials) }
    }

    /**
     * Called after successful transcription to decrement local trial count.
     * Note: Actual decrement happens in Firestore via AccessManager.
     */
    fun decrementTrials() {
        update { copy(freeTrialsRemaining = (freeTrialsRemaining - 1).coerceAtLeast(0)) }
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════

    fun showUpgradeDialog() = update { copy(showUpgradeDialog = true) }
    fun hideUpgradeDialog() = update { copy(showUpgradeDialog = false) }
    fun clearError() = update { copy(error = null) }

    // ═══════════════════════════════════════════════════════════════
    // HISTORY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    // Delete flow
    fun requestDelete(id: Long) = update { copy(deleteConfirmationId = id) }
    fun cancelDelete() = update { copy(deleteConfirmationId = null) }
    fun confirmDelete() {
        val id = _uiState.value.deleteConfirmationId ?: return
        viewModelScope.launch {
            historyUseCase.deleteTranscription(id)
            update { copy(deleteConfirmationId = null) }
        }
    }

    // Edit flow
    fun startEdit(t: Transcription) = update { copy(editingTranscription = t) }
    fun cancelEdit() = update { copy(editingTranscription = null) }
    fun saveEdit(text: String) {
        val t = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyUseCase.updateTranscription(t.id, text)
            update { copy(editingTranscription = null) }
        }
    }

    // AI improvement (for existing history items)
    fun onImproveWithAI(t: Transcription) {
        if (!_uiState.value.isPro) {
            showUpgradeDialog()
            return
        }
        update { copy(improvingId = t.id) }
        viewModelScope.launch {
            try {
                transcribeUseCase.improveText(t.text)?.let {
                    historyUseCase.updateTranscription(t.id, it)
                }
            } finally {
                update { copy(improvingId = null) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OBSERVERS
    // ═══════════════════════════════════════════════════════════════

    private fun observeEngineState() {
        viewModelScope.launch {
            sttEngine.state.collect { state ->
                val phase = when (state) {
                    is TranscriptionState.Ready -> RecordingPhase.READY
                    is TranscriptionState.Transcribing -> RecordingPhase.PROCESSING
                    else -> RecordingPhase.IDLE
                }
                update { copy(transcriptionState = state, recordingPhase = phase) }
            }
        }
    }

    private fun observeRecordingStatus() {
        viewModelScope.launch {
            audioRecorder.status.collect { status ->
                if (status is RecordingStatus.Error) {
                    update { copy(error = status.message, recordingPhase = RecordingPhase.READY) }
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyUseCase.getAllTranscriptions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { update { copy(history = it) } }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private inline fun update(block: MainUiState.() -> MainUiState) = _uiState.update(block)

    override fun onCleared() {
        audioRecorder.release()
        sttEngine.release()
    }
}