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
 * **Recording Session Manager (SSOT)**
 * 
 * This class acts as the centralized **Single Source of Truth** for the application's recording state.
 * It manages the complex orchestration between audio capturing, background services, 
 * cloud transcription, and history persistence.
 * 
 * **State Machine Flow:**
 * 1. **Ready:** Idle state, waiting for user input.
 * 2. **Recording:** Capturing audio bytes via [AudioRecorder]. Foreground service is active.
 * 3. **Processing:** Uploading & Transcribing (Chirp) -> Polishing (Gemini).
 * 4. **Success/Error:** Terminal states displaying the result.
 * 
 * **Key Responsibility:** 
 * Ensures the UI (via ViewModel) and the Android Service stay completely in sync 
 * by observing the single [state] flow exposed here.
 */
class RecordingSessionManager(
    private val audioRecorder: AudioRecorder,
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val historyRepository: TranscriptionRepository,
    private val serviceController: ServiceController
) {
    // ─────────────────────────────────────────────────────────────────────────────
    // STATE MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<AppState>(AppState.Ready)
    
    /**
     * Observable state flow. UI and Service observe this to update their appearance.
     * Use [resetState] to return to [AppState.Ready].
     */
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var transcriptionJob: Job? = null

    /**
     * Current transcription mode (Standard vs Polished).
     * Used by the UI to display the active toggle state.
     */
    var currentMode: TranscriptionMode = TranscriptionMode.STANDARD
        private set

    fun setMode(mode: TranscriptionMode) {
        currentMode = mode
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // RECORDING ACTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Starts the recording sequence.
     * 1. Starts the Android Foreground Service (required for mic access in background).
     * 2. Updates state to [AppState.Recording].
     * 3. Signals [AudioRecorder] to begin capturing bytes.
     */
    fun startRecording() {
        if (_state.value is AppState.Recording) return

        scope.launch {
            try {
                // Must start service BEFORE recording to guarantee mic permission context
                serviceController.startForeground()
                
                // Update state immediately so UI reflects "Recording" 
                _state.update { AppState.Recording }
                
                audioRecorder.startRecording()
            } catch (e: Exception) {
                // Return to error state but keep service alive if needed for retry
                _state.update { AppState.Error(e.message ?: "Failed to start recording") }
            }
        }
    }

    /**
     * Stops the recording and triggers the transcription pipeline.
     * 
     * Flow:
     * 1. Stop [AudioRecorder] -> Get raw PCM bytes.
     * 2. Check duration (fail if < 0.5s).
     * 3. Delegate to [TranscribeAudioUseCase] for Cloud/AI processing.
     * 4. Save result to [TransribtionRepository].
     */
    fun stopRecording() {
        if (_state.value !is AppState.Recording) return

        _state.update { AppState.Processing("Stopping...") }

        transcriptionJob = scope.launch {
            try {
                // Retrieve captured audio buffer
                val audioData = audioRecorder.stopRecording()
                val duration = AudioConfig.calculateDurationSeconds(audioData)

                // Validation: Prevent accidental clicks
                if (duration < 0.5f) {
                    _state.update { AppState.Error("Recording too short") }
                    return@launch
                }

                // Execute Use Case (Upload -> Transcribe -> Polish)
                val result = transcribeUseCase(
                    audioData = audioData,
                    mode = currentMode,
                    onProgress = { msg ->
                        _state.update { AppState.Processing(msg) }
                    }
                )

                // Handle Result
                result.fold(
                    onSuccess = { texts ->
                        // Persist to local database
                        historyRepository.saveTranscription(texts.original, texts.polished)
                        
                        // Display the relevant version based on mode
                        val displayText = texts.polished ?: texts.original
                        _state.update { AppState.Success(displayText) }
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

    /**
     * Aborts the current recording session.
     * Discards audio data and resets state to Ready.
     */
    fun cancelRecording() {
        scope.launch {
            try {
                // Safely stop components without processing data
                audioRecorder.cancelRecording()
                transcriptionJob?.cancel()
            } catch (e: Exception) {
                // Swallowing error on cancel is acceptable as we are resetting anyway
            } finally {
                _state.update { AppState.Ready }
            }
        }
    }

    /**
     * Resets the AppState to [AppState.Ready].
     * Called by UI after displaying a Success or Error dialog.
     */
    fun resetState() {
        _state.update { AppState.Ready }
    }
}