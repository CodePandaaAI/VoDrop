package com.liftley.vodrop.domain.manager

import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.audio.AudioRecorder
import com.liftley.vodrop.domain.model.AppState
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import com.liftley.vodrop.service.ServiceController
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
 * Orchestrates AudioRecorder, TranscribeUseCase, History, and Service.
 * 
 * NOTE: This is the ONLY class that holds and updates AppState.
 * All other classes observe this state.
 */
class RecordingSessionManager(
    private val audioRecorder: AudioRecorder,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyRepository: TranscriptionRepository,
    private val serviceController: ServiceController
) {
    private val _state = MutableStateFlow<AppState>(AppState.Ready)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var transcriptionJob: Job? = null

    // Config - public so UI can read current mode
    var currentMode: TranscriptionMode = TranscriptionMode.STANDARD
        private set

    fun setMode(mode: TranscriptionMode) {
        currentMode = mode
    }

    fun startRecording() {
        if (_state.value is AppState.Recording) return

        _state.update { AppState.Recording }
        
        scope.launch {
            try {
                // Start foreground service first (Android requirement)
                serviceController.startForeground()
                // Then start actual recording
                audioRecorder.startRecording()
            } catch (e: Exception) {
                _state.update { AppState.Error(e.message ?: "Failed to start recording") }
                serviceController.stopForeground()
            }
        }
    }

    fun stopRecording() {
        if (_state.value !is AppState.Recording) return

        _state.update { AppState.Processing("Stopping...") }

        transcriptionJob = scope.launch {
            try {
                val audioData = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                if (duration < 0.5f) {
                    _state.update { AppState.Error("Recording too short") }
                    return@launch
                }

                _state.update { AppState.Processing("Transcribing...") }

                val result = transcribeUseCase(
                    audioData = audioData,
                    mode = currentMode,
                    onProgress = { msg ->
                        _state.update { AppState.Processing(msg) }
                    },
                    onIntermediateResult = { /* Optional: preview updates */ }
                )

                result.fold(
                    onSuccess = { text ->
                        historyRepository.saveTranscription(text)
                        _state.update { AppState.Success(text) }
                    },
                    onFailure = { e ->
                        _state.update { AppState.Error(e.message ?: "Transcription failed") }
                    }
                )
            } catch (e: Exception) {
                _state.update { AppState.Error(e.message ?: "Transcription failed") }
            }
        }
    }

    fun cancelRecording() {
        scope.launch {
            try {
                audioRecorder.cancelRecording()
                transcriptionJob?.cancel()
            } catch (e: Exception) {
                // Ignore errors during cancel
            } finally {
                _state.update { AppState.Ready }
                serviceController.stopForeground()
            }
        }
    }

    fun resetState() {
        _state.update { AppState.Ready }
    }
}