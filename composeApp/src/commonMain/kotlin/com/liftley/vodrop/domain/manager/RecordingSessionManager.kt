package com.liftley.vodrop.domain.manager

import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.data.audio.RecordingStatus
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import com.liftley.vodrop.ui.main.TranscriptionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single Source of Truth for Recording State.
 * Orchestrates AudioRecorder, TranscribeUseCase, and History.
 * Survives across UI lifecycle changes (Singleton).
 */
class RecordingSessionManager(
    private val audioRecorder: AudioRecorder,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyUseCase: ManageHistoryUseCase
) {
    // Session State
    sealed interface SessionState {
        data object Idle : SessionState
        data class Recording(val amplitude: Float) : SessionState
        data class Processing(val message: String = "Processing...") : SessionState
        data class Success(val text: String) : SessionState
        data class Error(val message: String) : SessionState
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var transcriptionJob: Job? = null
    
    // Config
    private var currentMode: TranscriptionMode = TranscriptionMode.STANDARD

    init {
        // Observe AudioRecorder status (e.g. amplitude updates)
        scope.launch {
            audioRecorder.status.collect { status ->
                when (status) {
                    is RecordingStatus.Recording -> {
                        _state.update { SessionState.Recording(status.amplitudeDb) }
                    }
                    is RecordingStatus.Error -> {
                        _state.update { SessionState.Error(status.message) }
                    }
                    else -> { /* Idle handled manually */ }
                }
            }
        }
    }

    fun setMode(mode: TranscriptionMode) {
        currentMode = mode
    }

    fun startRecording() {
        if (_state.value is SessionState.Recording) return
        
        // Optimistic update to prevent double-starts and race conditions
        _state.update { SessionState.Recording(0f) }
        
        scope.launch {
            try {
                audioRecorder.startRecording()
                // redundant update but confirms successful start
                _state.update { SessionState.Recording(0f) }
            } catch (e: Exception) {
                _state.update { SessionState.Error(e.message ?: "Failed to start") }
            }
        }
    }

    fun stopRecording() {
        if (_state.value !is SessionState.Recording) return

        _state.update { SessionState.Processing("Stopping...") }

        transcriptionJob = scope.launch {
            try {
                val audioData = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                if (duration < 0.5f) {
                    _state.update { SessionState.Error("Recording too short") }
                    return@launch
                }

                _state.update { SessionState.Processing("Transcribing...") }

                // Call Transcribe UseCase
                val result = transcribeUseCase(
                    audioData = audioData,
                    mode = currentMode,
                    onProgress = { msg -> 
                        _state.update { SessionState.Processing(msg) } 
                    },
                    onIntermediateResult = { text ->
                        // Optional: update specific state if we want real-time preview
                    }
                )

                when (result) {
                    is TranscribeAudioUseCase.UseCaseResult.Success -> {
                        historyUseCase.saveTranscription(result.text)
                        _state.update { SessionState.Success(result.text) }
                    }
                    is TranscribeAudioUseCase.UseCaseResult.Error -> {
                        _state.update { SessionState.Error(result.message) }
                    }
                }
            } catch (e: Exception) {
                // Handle crashes if recorder fails or logic errors
                _state.update { SessionState.Error(e.message ?: "Transcription failed") }
            }
        }
    }

    fun cancelRecording() {
        scope.launch {
            try {
                audioRecorder.cancelRecording()
                transcriptionJob?.cancel()
                _state.update { SessionState.Idle }
            } catch (e: Exception) {
                _state.update { SessionState.Idle } // Force idle regardless
            }
        }
    }

    fun resetState() {
        _state.update { SessionState.Idle }
    }
}
