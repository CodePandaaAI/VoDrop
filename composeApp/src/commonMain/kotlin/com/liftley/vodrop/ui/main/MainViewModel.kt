package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.data.audio.*
import com.liftley.vodrop.data.stt.*
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase,
    private val platformAuth: PlatformAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        observeEngineState()
        observeRecordingStatus()
        loadHistory()
        initializeEngine()
    }

    // Recording
    fun onRecordClick() {
        val s = _uiState.value
        when {
            !s.isLoggedIn -> update { copy(error = "Please sign in first") }
            !s.canTranscribe -> update { copy(showUpgradeDialog = true) }
            s.recordingPhase == RecordingPhase.IDLE -> initializeEngine()
            s.recordingPhase == RecordingPhase.READY -> startRecording()
            s.recordingPhase == RecordingPhase.LISTENING -> stopRecording()
            else -> { }
        }
    }

    fun onCancelRecording() {
        if (_uiState.value.recordingPhase == RecordingPhase.LISTENING) {
            stopTimer()
            viewModelScope.launch {
                audioRecorder.cancelRecording()
                update { copy(recordingPhase = RecordingPhase.READY, currentTranscription = "", recordingDurationSeconds = 0, currentAmplitude = -60f) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    private fun initializeEngine() = viewModelScope.launch {
        runCatching { sttEngine.initialize() }.onFailure { update { copy(error = it.message) } }
    }

    private fun startRecording() = viewModelScope.launch {
        runCatching {
            audioRecorder.startRecording()
            update { copy(recordingPhase = RecordingPhase.LISTENING, currentTranscription = "", error = null) }
        }.onFailure { update { copy(error = it.message) } }
    }

    private fun stopRecording() {
        update { copy(recordingPhase = RecordingPhase.PROCESSING) }
        viewModelScope.launch {
            runCatching {
                val audio = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audio)
                if (duration < 0.5f) { update { copy(recordingPhase = RecordingPhase.READY, error = "Too short") }; return@launch }

                val result = transcribeUseCase(
                    audioData = audio,
                    mode = _uiState.value.transcriptionMode,
                    onProgress = { update { copy(progressMessage = it) } },
                    onIntermediateResult = { text -> update { copy(currentTranscription = text) } }
                )
                when (result) {
                    is TranscribeAudioUseCase.UseCaseResult.Success -> {
                        update { copy(currentTranscription = result.text, recordingPhase = RecordingPhase.READY, progressMessage = "") }
                        historyUseCase.saveTranscription(result.text)
                        viewModelScope.launch { platformAuth.recordUsage(duration.toLong()) }
                    }
                    is TranscribeAudioUseCase.UseCaseResult.Error -> update { copy(recordingPhase = RecordingPhase.READY, error = result.message, progressMessage = "") }
                }
            }.onFailure { update { copy(recordingPhase = RecordingPhase.READY, error = it.message, progressMessage = "") } }
        }
    }

    // Mode
    fun selectMode(mode: TranscriptionMode) {
        if (mode == TranscriptionMode.WITH_AI_POLISH && !_uiState.value.isPro) showUpgradeDialog()
        else update { copy(transcriptionMode = mode) }
    }

    // Auth (synced from Activity)
    fun setAuth(isLoggedIn: Boolean, isPro: Boolean, freeTrials: Int) {
        val c = _uiState.value
        if (c.isLoggedIn != isLoggedIn || c.isPro != isPro || c.freeTrialsRemaining != freeTrials) {
            update { copy(isLoggedIn = isLoggedIn, isPro = isPro, freeTrialsRemaining = freeTrials, error = if (isLoggedIn && !c.isLoggedIn) null else error) }
        }
    }

    // Dialogs
    fun showUpgradeDialog() = update { copy(showUpgradeDialog = true) }
    fun hideUpgradeDialog() = update { copy(showUpgradeDialog = false) }
    fun clearError() = update { copy(error = null) }
    fun openDrawer() = update { copy(isDrawerOpen = true) }
    fun closeDrawer() = update { copy(isDrawerOpen = false) }

    // History
    fun requestDelete(id: Long) = update { copy(deleteConfirmationId = id) }
    fun cancelDelete() = update { copy(deleteConfirmationId = null) }
    fun confirmDelete() {
        _uiState.value.deleteConfirmationId?.let { id ->
            viewModelScope.launch { historyUseCase.deleteTranscription(id); update { copy(deleteConfirmationId = null) } }
        }
    }

    fun startEdit(t: Transcription) = update { copy(editingTranscription = t, editText = t.text) }
    fun updateEditText(text: String) = update { copy(editText = text) }
    fun cancelEdit() = update { copy(editingTranscription = null, editText = "") }
    fun saveEdit() {
        val t = _uiState.value.editingTranscription ?: return
        viewModelScope.launch { historyUseCase.updateTranscription(t.id, _uiState.value.editText); update { copy(editingTranscription = null, editText = "") } }
    }

    fun onImproveWithAI(t: Transcription) {
        if (!_uiState.value.isPro) { showUpgradeDialog(); return }
        update { copy(improvingId = t.id) }
        viewModelScope.launch {
            try { transcribeUseCase.improveText(t.text)?.let { historyUseCase.updateTranscription(t.id, it) } }
            finally { update { copy(improvingId = null) } }
        }
    }

    // Observers
    private fun observeEngineState() = viewModelScope.launch {
        sttEngine.state.collect { s ->
            val phase = when (s) { is TranscriptionState.Ready -> RecordingPhase.READY; is TranscriptionState.Transcribing -> RecordingPhase.PROCESSING; else -> RecordingPhase.IDLE }
            update { copy(transcriptionState = s, recordingPhase = phase) }
        }
    }

    private fun observeRecordingStatus() = viewModelScope.launch {
        audioRecorder.status.collect { if (it is RecordingStatus.Error) update { copy(error = it.message, recordingPhase = RecordingPhase.READY) } }
    }

    private fun loadHistory() = viewModelScope.launch {
        historyUseCase.getAllTranscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()).collect { update { copy(history = it) } }
    }

    private inline fun update(block: MainUiState.() -> MainUiState) = _uiState.update(block)
    override fun onCleared() { audioRecorder.release(); sttEngine.release() }
}