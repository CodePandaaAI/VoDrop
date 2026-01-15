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

enum class RecordingPhase {
    IDLE,
    INITIALIZING_MODEL,
    READY,
    LISTENING,
    PROCESSING,
    COMPLETE
}

data class MainUiState(
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val modelState: ModelState = ModelState.NotLoaded,
    val currentTranscription: String = "",
    val history: List<Transcription> = emptyList(),
    val error: String? = null,
    val recordingAmplitudeDb: Float = 0f,
    val audioDurationSeconds: Float = 0f,
    val selectedModel: WhisperModel = WhisperModel.DEFAULT,
    val showModelSelector: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val showDeleteConfirmation: Long? = null,  // ID of transcription to delete
    val editingTranscription: Transcription? = null  // Transcription being edited
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
        checkFirstLaunch()
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch {
            val hasModel = WhisperModel.entries.any { sttEngine.isModelAvailable(it) }

            if (hasModel) {
                val availableModel = WhisperModel.entries.first { sttEngine.isModelAvailable(it) }
                _uiState.update {
                    it.copy(
                        selectedModel = availableModel,
                        isFirstLaunch = false,
                        showModelSelector = false
                    )
                }
                initializeModel(availableModel)
            } else {
                _uiState.update { it.copy(showModelSelector = true, isFirstLaunch = true) }
            }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            sttEngine.modelState.collect { modelState ->
                _uiState.update { it.copy(modelState = modelState) }

                when (modelState) {
                    is ModelState.Ready -> {
                        if (_uiState.value.recordingPhase == RecordingPhase.INITIALIZING_MODEL) {
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.READY) }
                        }
                    }
                    is ModelState.Error -> {
                        _uiState.update {
                            it.copy(error = modelState.message, recordingPhase = RecordingPhase.IDLE)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeRecordingStatus() {
        viewModelScope.launch {
            audioRecorder.status.collect { status ->
                when (status) {
                    is RecordingStatus.Recording -> {
                        _uiState.update { it.copy(recordingAmplitudeDb = status.amplitudeDb) }
                    }
                    is RecordingStatus.Error -> {
                        _uiState.update {
                            it.copy(error = status.message, recordingPhase = RecordingPhase.READY)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun initializeModel(model: WhisperModel = _uiState.value.selectedModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(recordingPhase = RecordingPhase.INITIALIZING_MODEL) }
            try {
                sttEngine.loadModel(model)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to load model: ${e.message}", recordingPhase = RecordingPhase.IDLE)
                }
            }
        }
    }

    fun selectModel(model: WhisperModel) {
        _uiState.update {
            it.copy(selectedModel = model, showModelSelector = false, isFirstLaunch = false)
        }
        initializeModel(model)
    }

    fun showModelSelector() {
        _uiState.update { it.copy(showModelSelector = true) }
    }

    fun hideModelSelector() {
        if (!_uiState.value.isFirstLaunch || _uiState.value.modelState is ModelState.Ready) {
            _uiState.update { it.copy(showModelSelector = false) }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getAllTranscriptions().collect { transcriptions ->
                _uiState.update { it.copy(history = transcriptions) }
            }
        }
    }

    fun onRecordClick() {
        when (_uiState.value.recordingPhase) {
            RecordingPhase.IDLE, RecordingPhase.INITIALIZING_MODEL -> {
                if (_uiState.value.modelState !is ModelState.Ready) {
                    initializeModel()
                }
            }
            RecordingPhase.READY, RecordingPhase.COMPLETE -> startRecording()
            RecordingPhase.LISTENING -> stopRecording()
            RecordingPhase.PROCESSING -> {}
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
                        error = null,
                        audioDurationSeconds = 0f
                    )
                }
            } catch (e: AudioRecorderException) {
                _uiState.update { it.copy(error = "Failed to start: ${e.message}") }
            }
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(recordingPhase = RecordingPhase.PROCESSING) }

        viewModelScope.launch {
            try {
                val audioData = withContext(Dispatchers.Default) { audioRecorder.stopRecording() }
                val durationSeconds = AudioConfig.calculateDurationSeconds(audioData)
                _uiState.update { it.copy(audioDurationSeconds = durationSeconds) }

                if (durationSeconds < 0.5f) {
                    _uiState.update {
                        it.copy(recordingPhase = RecordingPhase.READY, error = "Recording too short")
                    }
                    return@launch
                }

                val result = withContext(Dispatchers.Default) { sttEngine.transcribe(audioData) }

                when (result) {
                    is TranscriptionResult.Success -> {
                        val text = result.text.trim()
                        _uiState.update {
                            it.copy(currentTranscription = text, recordingPhase = RecordingPhase.COMPLETE)
                        }
                        if (text.isNotBlank()) {
                            repository.insertTranscription(formatCurrentTimestamp(), text)
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

    // Delete with confirmation
    fun requestDeleteTranscription(id: Long) {
        _uiState.update { it.copy(showDeleteConfirmation = id) }
    }

    fun confirmDeleteTranscription() {
        val id = _uiState.value.showDeleteConfirmation ?: return
        viewModelScope.launch {
            repository.deleteTranscription(id)
            _uiState.update { it.copy(showDeleteConfirmation = null) }
        }
    }

    fun cancelDeleteTranscription() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    // Edit transcription
    fun startEditTranscription(transcription: Transcription) {
        _uiState.update { it.copy(editingTranscription = transcription) }
    }

    fun saveEditTranscription(newText: String) {
        val transcription = _uiState.value.editingTranscription ?: return
        viewModelScope.launch {
            repository.updateTranscription(transcription.id, newText)
            _uiState.update { it.copy(editingTranscription = null) }
        }
    }

    fun cancelEditTranscription() {
        _uiState.update { it.copy(editingTranscription = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatCurrentTimestamp(): String {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.date} ${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        sttEngine.release()
    }
}