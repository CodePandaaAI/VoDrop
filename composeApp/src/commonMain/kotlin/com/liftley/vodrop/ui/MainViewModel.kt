package com.liftley.vodrop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.audio.AudioConfig
import com.liftley.vodrop.audio.AudioRecorder
import com.liftley.vodrop.audio.AudioRecorderException
import com.liftley.vodrop.audio.RecordingStatus
import com.liftley.vodrop.model.Transcription
import com.liftley.vodrop.repository.TranscriptionRepository
import com.liftley.vodrop.stt.ModelState
import com.liftley.vodrop.stt.SpeechToTextEngine
import com.liftley.vodrop.stt.TranscriptionResult
import com.liftley.vodrop.stt.WhisperModel
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

enum class RecordingPhase {
    IDLE,
    READY,
    LISTENING,
    PROCESSING
}

data class MainUiState(
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val modelState: ModelState = ModelState.NotLoaded,
    val selectedModel: WhisperModel = WhisperModel.DEFAULT,
    val currentTranscription: String = "",
    val history: List<Transcription> = emptyList(),
    val error: String? = null,

    // Dialogs
    val showModelSelector: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,

    // Pro features
    val isPro: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userPhotoUrl: String? = null,
    val showProfileDialog: Boolean = false,
    val showUpgradeDialog: Boolean = false,
    val showLoginPrompt: Boolean = false,
    val userEmail: String? = null,
    val improvingTranscriptionId: Long? = null
)

class MainViewModel(
    private val repository: TranscriptionRepository,
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine
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
                // Simulate AI improvement (replace with actual Gemini call later)
                val improvedText = withContext(Dispatchers.IO) {
                    delay(2000) // Simulate API call
                    improveText(transcription.text)
                }

                // Update the transcription in database
                repository.updateTranscription(transcription.id, improvedText)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to improve: ${e.message}") }
            } finally {
                _uiState.update { it.copy(improvingTranscriptionId = null) }
            }
        }
    }

    // Simple text improvement (placeholder - will use Gemini later)
    private fun improveText(text: String): String {
        return text
            .replace(Regex("\\bum+\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\buh+\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\blike\\b,?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { it.uppercaseChar() }
            .let { if (!it.endsWith(".") && !it.endsWith("!") && !it.endsWith("?")) "$it." else it }
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

                val result = withContext(Dispatchers.Default) { sttEngine.transcribe(audioData) }

                when (result) {
                    is TranscriptionResult.Success -> {
                        val text = result.text.trim()
                        _uiState.update {
                            it.copy(currentTranscription = text, recordingPhase = RecordingPhase.READY)
                        }
                        if (text.isNotBlank()) {
                            repository.insertTranscription(formatTimestamp(), text)
                        }
                    }
                    is TranscriptionResult.Error -> {
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