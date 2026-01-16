package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.AudioRecorderException
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.data.stt.ModelState
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.WhisperModel
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

    private var modelStateJob: Job? = null
    private var recordingStatusJob: Job? = null
    private var historyJob: Job? = null
    private var inactivityCheckJob: Job? = null

    init {
        loadHistory()
        observeModelState()
        observeRecordingStatus()
        initializeOnStartup()
        startInactivityChecker()
    }

    // ========== Transcription Mode ==========

    fun cycleTranscriptionMode() {
        _uiState.update {
            val entries = TranscriptionMode.entries
            val currentIndex = entries.indexOf(it.transcriptionMode)
            val nextIndex = (currentIndex + 1) % entries.size
            val newMode = entries[nextIndex]
            println("🔄 Switching to: ${newMode.displayName}")
            it.copy(transcriptionMode = newMode)
        }
    }

    // ========== Auth State (set from Activity) ==========

    fun setProStatus(isPro: Boolean) {
        _uiState.update { it.copy(isPro = isPro) }
    }

    fun setUserInfo(isLoggedIn: Boolean, name: String?, email: String?, photoUrl: String?) {
        _uiState.update {
            it.copy(isLoggedIn = isLoggedIn, userName = name, userEmail = email, userPhotoUrl = photoUrl)
        }
    }

    // ========== Dialog Visibility ==========

    fun showUpgradeDialog() = _uiState.update { it.copy(showUpgradeDialog = true) }
    fun hideUpgradeDialog() = _uiState.update { it.copy(showUpgradeDialog = false) }
    fun showLoginPrompt() = _uiState.update { it.copy(showLoginPrompt = true) }
    fun hideLoginPrompt() = _uiState.update { it.copy(showLoginPrompt = false) }
    fun showProfileDialog() = _uiState.update { it.copy(showProfileDialog = true) }
    fun hideProfileDialog() = _uiState.update { it.copy(showProfileDialog = false) }
    fun showModelSelector() = _uiState.update { it.copy(showModelSelector = true) }

    fun hideModelSelector() {
        val state = _uiState.value
        if (!state.isFirstLaunch || state.modelState is ModelState.Ready) {
            _uiState.update { it.copy(showModelSelector = false) }
        }
    }

    // ========== Model Management ==========

    fun selectModel(model: WhisperModel) {
        _uiState.update { it.copy(selectedModel = model, showModelSelector = false, isFirstLaunch = false) }
        loadModel(model)
    }

    private fun loadModel(model: WhisperModel) {
        viewModelScope.launch {
            try {
                sttEngine.loadModel(model)
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Download timed out. Please check your internet connection and try again."
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your connection."
                    else -> "Failed to load model: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        error = errorMessage,
                        recordingPhase = RecordingPhase.IDLE,
                        modelState = ModelState.NotLoaded
                    )
                }
            }
        }
    }

    // ========== Recording Flow ==========

    fun onRecordClick() {
        val state = _uiState.value
        when (state.recordingPhase) {
            RecordingPhase.IDLE -> if (state.modelState !is ModelState.Ready) loadModel(state.selectedModel)
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { /* ignore */ }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                audioRecorder.startRecording()
                _uiState.update { it.copy(recordingPhase = RecordingPhase.LISTENING, currentTranscription = "", error = null) }
            } catch (e: AudioRecorderException) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(recordingPhase = RecordingPhase.PROCESSING) }

        viewModelScope.launch {
            try {
                val audioData = withContext(Dispatchers.Default) { audioRecorder.stopRecording() }
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                if (duration < 0.5f) {
                    _uiState.update { it.copy(recordingPhase = RecordingPhase.READY, error = "Recording too short (min 0.5s)", currentTranscription = "") }
                    return@launch
                }

                val mode = _uiState.value.transcriptionMode
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🎤 Mode: ${mode.displayName}")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Delegate to use case
                val result = transcribeUseCase(
                    audioData = audioData,
                    mode = mode,
                    onProgress = { progress -> _uiState.update { it.copy(currentTranscription = progress) } }
                )

                when (result) {
                    is TranscribeAudioUseCase.Result.Success -> {
                        _uiState.update { it.copy(currentTranscription = result.text, recordingPhase = RecordingPhase.READY) }
                        historyUseCase.saveTranscription(result.text)
                        println("🎉 Complete! Cloud: ${result.usedCloud}, AI: ${result.usedAI}")
                    }
                    is TranscribeAudioUseCase.Result.Error -> {
                        _uiState.update { it.copy(recordingPhase = RecordingPhase.READY, error = result.message, currentTranscription = "") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(recordingPhase = RecordingPhase.READY, error = "Error: ${e.message}", currentTranscription = "") }
            }
        }
    }

    // ========== AI Improvement (Pro Feature) ==========

    fun onImproveWithAI(transcription: Transcription) {
        val state = _uiState.value

        if (!state.isLoggedIn) { showLoginPrompt(); return }
        if (!state.isPro) { showUpgradeDialog(); return }

        _uiState.update { it.copy(improvingTranscriptionId = transcription.id) }

        viewModelScope.launch {
            try {
                val result = transcribeUseCase.invoke(
                    audioData = ByteArray(0), // Not used for text improvement
                    mode = TranscriptionMode.OFFLINE_WITH_AI
                )
                // Note: For actual text improvement, we'd need a separate method
                // This is a placeholder - real implementation would call textCleanupService directly
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

    fun startEdit(transcription: Transcription) = _uiState.update { it.copy(editingTranscription = transcription) }
    fun cancelEdit() = _uiState.update { it.copy(editingTranscription = null) }

    fun saveEdit(newText: String) {
        val transcription = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            historyUseCase.updateTranscription(transcription.id, newText)
            _uiState.update { it.copy(editingTranscription = null) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ========== Observers & Lifecycle ==========

    private fun initializeOnStartup() {
        viewModelScope.launch {
            val availableModel = WhisperModel.entries.firstOrNull { sttEngine.isModelAvailable(it) }
            if (availableModel != null) {
                _uiState.update { it.copy(selectedModel = availableModel, isFirstLaunch = false, showModelSelector = false) }
                loadModel(availableModel)
            } else {
                _uiState.update { it.copy(showModelSelector = true, isFirstLaunch = true) }
            }
        }
    }

    private fun observeModelState() {
        modelStateJob?.cancel()
        modelStateJob = viewModelScope.launch {
            sttEngine.modelState.collect { state ->
                _uiState.update { current ->
                    val newPhase = when (state) {
                        is ModelState.Ready -> RecordingPhase.READY
                        else -> RecordingPhase.IDLE
                    }
                    val error = if (state is ModelState.Error) state.message else current.error
                    current.copy(modelState = state, recordingPhase = newPhase, error = error)
                }
            }
        }
    }

    private fun observeRecordingStatus() {
        recordingStatusJob?.cancel()
        recordingStatusJob = viewModelScope.launch {
            audioRecorder.status.collect { status ->
                if (status is RecordingStatus.Error) {
                    _uiState.update { it.copy(error = status.message, recordingPhase = RecordingPhase.READY) }
                }
            }
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            historyUseCase.getAllTranscriptions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { list -> _uiState.update { it.copy(history = list) } }
        }
    }

    private fun startInactivityChecker() {
        inactivityCheckJob?.cancel()
        inactivityCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                if (_uiState.value.recordingPhase == RecordingPhase.READY) {
                    sttEngine.checkAndUnloadIfInactive()
                }
            }
        }
    }

    // Add these functions to MainViewModel:

    fun showTranscriptionModeSheet() {
        _uiState.update { it.copy(showTranscriptionModeSheet = true) }
    }

    fun hideTranscriptionModeSheet() {
        _uiState.update { it.copy(showTranscriptionModeSheet = false) }
    }

    fun selectTranscriptionMode(mode: TranscriptionMode) {
        println("🔄 Selected mode: ${mode.displayName}")
        _uiState.update {
            it.copy(
                transcriptionMode = mode,
                showTranscriptionModeSheet = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelStateJob?.cancel()
        recordingStatusJob?.cancel()
        historyJob?.cancel()
        inactivityCheckJob?.cancel()
        audioRecorder.release()
        sttEngine.release()
    }
}