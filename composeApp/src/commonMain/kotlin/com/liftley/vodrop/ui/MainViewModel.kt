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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Simplified recording states - reduced from 6 to 4 phases
 */
enum class RecordingPhase {
    IDLE,           // Model not ready, waiting for initialization
    READY,          // Ready to record
    LISTENING,      // Currently recording audio
    PROCESSING      // Transcribing recorded audio
}

/**
 * Single source of truth for all UI state
 */
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
    val editingTranscription: Transcription? = null
)

class MainViewModel(
    private val repository: TranscriptionRepository,
    private val audioRecorder: AudioRecorder,
    private val sttEngine: SpeechToTextEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
        observeModelState()
        observeRecordingStatus()
        initializeOnStartup()
    }

    // ========== Initialization ==========

    private fun initializeOnStartup() {
        viewModelScope.launch {
            // Check if any model is already downloaded
            val availableModel = WhisperModel.entries.firstOrNull { sttEngine.isModelAvailable(it) }

            if (availableModel != null) {
                // Model exists, load it
                _uiState.update {
                    it.copy(
                        selectedModel = availableModel,
                        isFirstLaunch = false,
                        showModelSelector = false
                    )
                }
                loadModel(availableModel)
            } else {
                // First launch, show model selector
                _uiState.update { it.copy(showModelSelector = true, isFirstLaunch = true) }
            }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            sttEngine.modelState.collect { state ->
                _uiState.update { current ->
                    val newPhase = when (state) {
                        is ModelState.Ready -> RecordingPhase.READY
                        is ModelState.Error -> RecordingPhase.IDLE
                        else -> current.recordingPhase
                    }
                    val error = if (state is ModelState.Error) state.message else current.error
                    current.copy(modelState = state, recordingPhase = newPhase, error = error)
                }
            }
        }
    }

    private fun observeRecordingStatus() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.getAllTranscriptions().collect { list ->
                _uiState.update { it.copy(history = list) }
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
        // Can only hide if not first launch or model is ready
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
                // Try to load model if not ready
                if (state.modelState !is ModelState.Ready) {
                    loadModel(state.selectedModel)
                }
            }
            RecordingPhase.READY -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> { /* Ignore clicks while processing */ }
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
                        it.copy(recordingPhase = RecordingPhase.READY, error = "Recording too short (min 0.5s)")
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
                            it.copy(recordingPhase = RecordingPhase.READY, error = result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(recordingPhase = RecordingPhase.READY, error = "Error: ${e.message}")
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

    // ========== Utilities ==========

    private fun formatTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.date} ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        sttEngine.release()
    }
}