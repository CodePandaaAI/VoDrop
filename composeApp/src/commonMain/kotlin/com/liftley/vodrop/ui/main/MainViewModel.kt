package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.AudioRecorderException
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var engineStateJob: Job? = null
    private var recordingStatusJob: Job? = null
    private var historyJob: Job? = null

    init {
        loadHistory()
        observeEngineState()
        observeRecordingStatus()
        initializeEngine()
    }

    // ========== Engine Initialization ==========

    private fun initializeEngine() {
        viewModelScope.launch {
            try {
                sttEngine.initialize()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to initialize: ${e.message}",
                        recordingPhase = RecordingPhase.IDLE
                    )
                }
            }
        }
    }

    // ========== Transcription Mode ==========

    fun showTranscriptionModeSheet() {
        _uiState.update { it.copy(showTranscriptionModeSheet = true) }
    }

    fun hideTranscriptionModeSheet() {
        _uiState.update { it.copy(showTranscriptionModeSheet = false) }
    }

    fun selectTranscriptionMode(mode: TranscriptionMode) {
        // Check if user has access to Pro modes
        if (mode.requiresPro && !_uiState.value.isPro) {
            showUpgradeDialog()
            return
        }

        println("🔄 Selected mode: ${mode.displayName}")
        _uiState.update {
            it.copy(
                transcriptionMode = mode,
                showTranscriptionModeSheet = false
            )
        }
    }

    // ========== Auth State (set from Activity) ==========

    fun setProStatus(isPro: Boolean) {
        _uiState.update { it.copy(isPro = isPro) }
    }

    fun setUserInfo(isLoggedIn: Boolean, name: String?, email: String?, photoUrl: String?) {
        _uiState.update {
            it.copy(
                isLoggedIn = isLoggedIn,
                userName = name ?: "",  // Convert null to empty string
                userEmail = email,
                userPhotoUrl = photoUrl
            )
        }
    }

    // ========== Dialog Visibility ==========

    fun showUpgradeDialog() = _uiState.update { it.copy(showUpgradeDialog = true) }
    fun hideUpgradeDialog() = _uiState.update { it.copy(showUpgradeDialog = false) }
    fun showLoginPrompt() = _uiState.update { it.copy(showLoginPrompt = true) }
    fun hideLoginPrompt() = _uiState.update { it.copy(showLoginPrompt = false) }
    fun showProfileDialog() = _uiState.update { it.copy(showProfileDialog = true) }
    fun hideProfileDialog() = _uiState.update { it.copy(showProfileDialog = false) }

    // ========== Recording Flow ==========

    fun onRecordClick() {
        val state = _uiState.value

        // Check if user has quota
        if (!state.canTranscribe) {
            showUpgradeDialog()
            return
        }

        when (state.recordingPhase) {
            RecordingPhase.IDLE -> {
                // Try to initialize engine
                initializeEngine()
            }
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { /* ignore clicks while processing */ }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                audioRecorder.startRecording()
                _uiState.update {
                    it.copy(
                        recordingPhase = RecordingPhase.LISTENING,
                        currentTranscription = "",
                        error = null
                    )
                }
            } catch (e: AudioRecorderException) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(recordingPhase = RecordingPhase.PROCESSING) }

        viewModelScope.launch {
            try {
                val audioData = withContext(Dispatchers.Default) {
                    audioRecorder.stopRecording()
                }
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                if (duration < 0.5f) {
                    _uiState.update {
                        it.copy(
                            recordingPhase = RecordingPhase.READY,
                            error = "Recording too short (min 0.5s)",
                            currentTranscription = ""
                        )
                    }
                    return@launch
                }

                val mode = _uiState.value.transcriptionMode
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🎤 Mode: ${mode.displayName}")
                println("📊 Audio: ${audioData.size} bytes, ${duration}s")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Show progress
                _uiState.update { it.copy(currentTranscription = "☁️ Transcribing...") }

                // Perform transcription
                val result = transcribeUseCase(
                    audioData = audioData,
                    mode = mode,
                    onProgress = { progress ->
                        _uiState.update { it.copy(currentTranscription = progress) }
                    }
                )

                when (result) {
                    is TranscribeAudioUseCase.Result.Success -> {
                        _uiState.update {
                            it.copy(
                                currentTranscription = result.text,
                                recordingPhase = RecordingPhase.READY,
                                // Increment usage count for free users
                                monthlyTranscriptions = if (!it.isPro) it.monthlyTranscriptions + 1 else it.monthlyTranscriptions
                            )
                        }
                        historyUseCase.saveTranscription(result.text)
                        println("🎉 Complete! AI Polish: ${result.usedAI}")
                    }
                    is TranscribeAudioUseCase.Result.Error -> {
                        _uiState.update {
                            it.copy(
                                recordingPhase = RecordingPhase.READY,
                                error = result.message,
                                currentTranscription = ""
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        recordingPhase = RecordingPhase.READY,
                        error = "Error: ${e.message}",
                        currentTranscription = ""
                    )
                }
            }
        }
    }

    // ========== Settings ==========

    fun showSettings() = _uiState.update { it.copy(showSettings = true) }
    fun hideSettings() = _uiState.update { it.copy(showSettings = false) }

    fun setCleanupStyle(style: CleanupStyle) {
        viewModelScope.launch {
            // TODO: Save to PreferencesManager
            _uiState.update { it.copy(cleanupStyle = style) }
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            // TODO: Save to PreferencesManager
            _uiState.update { it.copy(userName = name) }
        }
    }

    // ========== AI Improvement (Pro Feature) ==========

    fun onImproveWithAI(transcription: Transcription) {
        val state = _uiState.value

        if (!state.isLoggedIn) {
            showLoginPrompt()
            return
        }
        if (!state.isPro) {
            showUpgradeDialog()
            return
        }

        _uiState.update { it.copy(improvingTranscriptionId = transcription.id) }

        viewModelScope.launch {
            try {
                // Use the text cleanup service directly for improvement
                val result = transcribeUseCase.improveText(transcription.text)
                if (result != null) {
                    historyUseCase.updateTranscription(transcription.id, result)
                }
            } finally {
                _uiState.update { it.copy(improvingTranscriptionId = null) }
            }
        }
    }

    // ========== History CRUD ==========

    fun requestDelete(id: Long) = _uiState.update { it.copy(deleteConfirmationId = id) }
    fun cancelDelete() = _uiState.update { it.copy(deleteConfirmationId = null) }

    fun confirmDelete() {
        val id = _uiState.value.deleteConfirmationId ?: return
        viewModelScope.launch {
            historyUseCase.deleteTranscription(id)
            _uiState.update { it.copy(deleteConfirmationId = null) }
        }
    }

    fun startEdit(transcription: Transcription) =
        _uiState.update { it.copy(editingTranscription = transcription) }

    fun cancelEdit() =
        _uiState.update { it.copy(editingTranscription = null) }

    fun saveEdit(newText: String) {
        val transcription = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyUseCase.updateTranscription(transcription.id, newText)
            _uiState.update { it.copy(editingTranscription = null) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ========== Observers ==========

    private fun observeEngineState() {
        engineStateJob?.cancel()
        engineStateJob = viewModelScope.launch {
            sttEngine.state.collect { state ->
                _uiState.update { current ->
                    val newPhase = when (state) {
                        is TranscriptionState.Ready -> RecordingPhase.READY
                        is TranscriptionState.Transcribing -> RecordingPhase.PROCESSING
                        is TranscriptionState.Error -> RecordingPhase.IDLE
                        is TranscriptionState.Downloading -> RecordingPhase.IDLE
                        is TranscriptionState.Initializing -> RecordingPhase.IDLE
                        is TranscriptionState.NotReady -> RecordingPhase.IDLE
                    }
                    val error = if (state is TranscriptionState.Error) state.message else current.error
                    current.copy(
                        transcriptionState = state,
                        recordingPhase = newPhase,
                        error = error
                    )
                }
            }
        }
    }

    private fun observeRecordingStatus() {
        recordingStatusJob?.cancel()
        recordingStatusJob = viewModelScope.launch {
            audioRecorder.status.collect { status ->
                if (status is RecordingStatus.Error) {
                    _uiState.update {
                        it.copy(
                            error = status.message,
                            recordingPhase = RecordingPhase.READY
                        )
                    }
                }
            }
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            historyUseCase.getAllTranscriptions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { list ->
                    _uiState.update { it.copy(history = list) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engineStateJob?.cancel()
        recordingStatusJob?.cancel()
        historyJob?.cancel()
        audioRecorder.release()
        sttEngine.release()
    }
}