package com.liftley.vodrop.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.AudioRecorderException
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.data.stt.GroqWhisperService
import com.liftley.vodrop.data.stt.ModelState
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.data.stt.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MainViewModel(
    private val repository: TranscriptionRepository,
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine,
    private val groqService: GroqWhisperService,
    private val textCleanupService: TextCleanupService  // Added
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

    // ========== Transcription Mode Toggle ==========

    fun cycleTranscriptionMode() {
        _uiState.update {
            val entries = TranscriptionMode.entries
            val currentIndex = entries.indexOf(it.transcriptionMode)
            val nextIndex = (currentIndex + 1) % entries.size
            val newMode = entries[nextIndex]
            println("🔄 Switching transcription mode to: ${newMode.displayName}")
            it.copy(transcriptionMode = newMode)
        }
    }

    private fun getModeDescription(mode: TranscriptionMode): String = mode.displayName

    // ========== Pro / Auth State ==========

    fun setProStatus(isPro: Boolean) {
        _uiState.update { it.copy(isPro = isPro) }
    }

    fun setUserInfo(isLoggedIn: Boolean, name: String?, email: String?, photoUrl: String?) {
        _uiState.update {
            it.copy(
                isLoggedIn = isLoggedIn,
                userName = name,
                userEmail = email,
                userPhotoUrl = photoUrl
            )
        }
    }

    fun showUpgradeDialog() {
        _uiState.update { it.copy(showUpgradeDialog = true) }
    }

    fun hideUpgradeDialog() {
        _uiState.update { it.copy(showUpgradeDialog = false) }
    }

    fun showLoginPrompt() {
        _uiState.update { it.copy(showLoginPrompt = true) }
    }

    fun hideLoginPrompt() {
        _uiState.update { it.copy(showLoginPrompt = false) }
    }

    fun showProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = true) }
    }

    fun hideProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = false) }
    }

    fun onImproveWithAI(transcription: Transcription) {
        val state = _uiState.value

        // If not logged in, prompt login first
        if (!state.isLoggedIn) {
            showLoginPrompt()
            return
        }

        // If not pro, show upgrade dialog
        if (!state.isPro) {
            showUpgradeDialog()
            return
        }

        // Start improvement
        _uiState.update { it.copy(improvingTranscriptionId = transcription.id) }

        viewModelScope.launch {
            try {
                val improvedText = withContext(Dispatchers.IO) {
                    improveTextWithGemini(transcription.text)
                }

                if (improvedText != null) {
                    repository.updateTranscription(transcription.id, improvedText)
                } else {
                    _uiState.update { it.copy(error = "Failed to improve text") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to improve: ${e.message}") }
            } finally {
                _uiState.update { it.copy(improvingTranscriptionId = null) }
            }
        }
    }

    // ========== Initialization ==========

    private fun initializeOnStartup() {
        viewModelScope.launch {
            val availableModel = WhisperModel.entries.firstOrNull { sttEngine.isModelAvailable(it) }

            if (availableModel != null) {
                _uiState.update {
                    it.copy(
                        selectedModel = availableModel,
                        isFirstLaunch = false,
                        showModelSelector = false
                    )
                }
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
                        is ModelState.Error -> RecordingPhase.IDLE
                        is ModelState.NotLoaded -> RecordingPhase.IDLE
                        is ModelState.Downloading -> RecordingPhase.IDLE
                        is ModelState.Loading -> RecordingPhase.IDLE
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
                    _uiState.update {
                        it.copy(error = status.message, recordingPhase = RecordingPhase.READY)
                    }
                }
            }
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.getAllTranscriptions()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
                .collect { list ->
                    _uiState.update { it.copy(history = list) }
                }
        }
    }

    private fun startInactivityChecker() {
        inactivityCheckJob?.cancel()
        inactivityCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                val state = _uiState.value
                if (state.recordingPhase == RecordingPhase.READY) {
                    sttEngine.checkAndUnloadIfInactive()
                }
            }
        }
    }

    // ========== Model Selection ==========

    fun selectModel(model: WhisperModel) {
        _uiState.update {
            it.copy(selectedModel = model, showModelSelector = false, isFirstLaunch = false)
        }
        loadModel(model)
    }

    fun showModelSelector() {
        _uiState.update { it.copy(showModelSelector = true) }
    }

    fun hideModelSelector() {
        val state = _uiState.value
        if (!state.isFirstLaunch || state.modelState is ModelState.Ready) {
            _uiState.update { it.copy(showModelSelector = false) }
        }
    }

    private fun loadModel(model: WhisperModel) {
        viewModelScope.launch {
            try {
                sttEngine.loadModel(model)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to load model: ${e.message}", recordingPhase = RecordingPhase.IDLE)
                }
            }
        }
    }

    // ========== Recording ==========

    fun onRecordClick() {
        val state = _uiState.value
        when (state.recordingPhase) {
            RecordingPhase.IDLE -> {
                if (state.modelState !is ModelState.Ready) {
                    loadModel(state.selectedModel)
                }
            }
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { }
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
                val audioData = withContext(Dispatchers.Default) { audioRecorder.stopRecording() }
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

                val transcriptionMode = _uiState.value.transcriptionMode

                // Log which mode we're using
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🎤 Starting transcription...")
                println("📊 Mode: ${getModeDescription(transcriptionMode)}")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Step 1: Transcription (Offline vs Cloud)
                val result: TranscriptionResult = when (transcriptionMode) {
                    TranscriptionMode.CLOUD_WITH_AI -> {
                        // Cloud mode: Use Groq Whisper Large v3
                        _uiState.update { it.copy(currentTranscription = "☁️ Transcribing with cloud...") }
                        println("☁️ Using Groq Whisper (cloud)")
                        transcribeWithGroq(audioData)
                    }
                    else -> {
                        // Offline modes (0 and 1): Use local Whisper.cpp
                        _uiState.update { it.copy(currentTranscription = "📱 Transcribing locally...") }
                        println("📱 Using Whisper.cpp (offline)")
                        withContext(Dispatchers.Default) { sttEngine.transcribe(audioData) }
                    }
                }

                // Step 2: Process result
                when (result) {
                    is TranscriptionResult.Success -> {
                        var text = result.text.trim()
                        println("✅ Transcription result: $text")

                        // Step 3: Apply AI cleanup (only in modes 1 and 2)
                        if (transcriptionMode != TranscriptionMode.OFFLINE_ONLY &&
                            text.isNotBlank() &&
                            text.length > 30
                        ) {
                            _uiState.update {
                                it.copy(currentTranscription = "$text\n\n⏳ Improving with AI...")
                            }
                            println("🤖 Applying Gemini LLM cleanup...")

                            val improvedText = improveTextWithGemini(text)
                            if (improvedText != null) {
                                println("✅ Gemini improvement applied")
                                text = improvedText
                            } else {
                                println("⚠️ Gemini improvement failed, using original")
                            }
                        } else {
                            println("⏭️ Skipping AI cleanup (mode=$transcriptionMode, length=${text.length})")
                        }

                        _uiState.update {
                            it.copy(currentTranscription = text, recordingPhase = RecordingPhase.READY)
                        }

                        if (text.isNotBlank()) {
                            repository.insertTranscription(formatTimestamp(), text)
                        }

                        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        println("🎉 Transcription complete!")
                        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    }
                    is TranscriptionResult.Error -> {
                        println("❌ Transcription error: ${result.message}")
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
                println("❌ Exception: ${e.message}")
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

    /**
     * Transcribe using Groq's Whisper Large v3 (cloud)
     */
    private suspend fun transcribeWithGroq(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            groqService.transcribe(audioData)
        }
    }

    /**
     * Improve text using injected TextCleanupService (Gemini)
     */
    private suspend fun improveTextWithGemini(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!textCleanupService.isAvailable()) {
                    println("⚠️ Text cleanup service not available")
                    return@withContext null
                }

                val result = textCleanupService.cleanupText(text)

                if (result.isSuccess) {
                    println("✅ Gemini cleanup successful")
                    result.getOrNull()
                } else {
                    println("⚠️ Gemini cleanup failed: ${result.exceptionOrNull()?.message}")
                    null
                }
            } catch (e: Exception) {
                println("❌ Gemini error: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    // ========== History Actions ==========

    fun requestDelete(id: Long) {
        _uiState.update { it.copy(deleteConfirmationId = id) }
    }

    fun confirmDelete() {
        val id = _uiState.value.deleteConfirmationId ?: return
        viewModelScope.launch {
            repository.deleteTranscription(id)
            _uiState.update { it.copy(deleteConfirmationId = null) }
        }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deleteConfirmationId = null) }
    }

    fun startEdit(transcription: Transcription) {
        _uiState.update { it.copy(editingTranscription = transcription) }
    }

    fun saveEdit(newText: String) {
        val transcription = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            repository.updateTranscription(transcription.id, newText)
            _uiState.update { it.copy(editingTranscription = null) }
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingTranscription = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.date} ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
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