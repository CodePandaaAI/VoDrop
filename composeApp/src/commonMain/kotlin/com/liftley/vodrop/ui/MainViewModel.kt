package com.liftley.vodrop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.vodrop.audio.AudioRecorder
import com.liftley.vodrop.model.Transcription
import com.liftley.vodrop.repository.TranscriptionRepository
import com.liftley.vodrop.stt.SpeechToTextEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class RecordingState {
    READY,
    LISTENING,
    PROCESSING
}

data class MainUiState(
    val recordingState: RecordingState = RecordingState.READY,
    val currentTranscription: String = "",
    val history: List<Transcription> = emptyList(),
    val error: String? = null
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
        preloadModel()
    }

    private fun preloadModel() {
        viewModelScope.launch {
            try {
                sttEngine.loadModel()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load model: ${e.message}")
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getAllTranscriptions().collect { transcriptions ->
                _uiState.value = _uiState.value.copy(history = transcriptions)
            }
        }
    }

    fun onRecordClick() {
        when (_uiState.value.recordingState) {
            RecordingState.READY -> startRecording()
            RecordingState.LISTENING -> stopRecording()
            RecordingState.PROCESSING -> { /* Do nothing */ }
        }
    }

    private fun startRecording() {
        try {
            audioRecorder.startRecording()
            _uiState.value = _uiState.value.copy(
                recordingState = RecordingState.LISTENING,
                currentTranscription = "",
                error = null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Failed to start recording: ${e.message}")
        }
    }

    private fun stopRecording() {
        _uiState.value = _uiState.value.copy(recordingState = RecordingState.PROCESSING)
        
        viewModelScope.launch {
            try {
                val audioData = withContext(Dispatchers.Default) {
                    audioRecorder.stopRecording()
                }
                
                val transcription = withContext(Dispatchers.Default) {
                    sttEngine.transcribe(audioData)
                }
                
                _uiState.value = _uiState.value.copy(
                    currentTranscription = transcription,
                    recordingState = RecordingState.READY
                )
                
                if (transcription.isNotBlank()) {
                    val now = Clock.System.now()
                    val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
                    val timestamp = "${localDateTime.date} ${localDateTime.hour}:${localDateTime.minute.toString().padStart(2, '0')}"
                    repository.insertTranscription(timestamp, transcription)
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingState.READY,
                    error = "Transcription failed: ${e.message}"
                )
            }
        }
    }

    fun deleteTranscription(id: Long) {
        viewModelScope.launch {
            repository.deleteTranscription(id)
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}