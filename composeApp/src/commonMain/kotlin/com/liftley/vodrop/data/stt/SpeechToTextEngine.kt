package com.liftley.vodrop.data.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Transcription engine state
 * - For Cloud (Android): Usually Ready or Transcribing
 * - For Offline (Desktop): May need Downloading/Initializing states
 */
sealed interface TranscriptionState {
    data object NotReady : TranscriptionState
    data class Initializing(val message: String = "Initializing...") : TranscriptionState
    data class Downloading(val progress: Float) : TranscriptionState  // Desktop only
    data object Ready : TranscriptionState
    data object Transcribing : TranscriptionState
    data class Error(val message: String) : TranscriptionState
}

/**
 * Result of a transcription operation
 */
sealed interface TranscriptionResult {
    data class Success(val text: String, val durationMs: Long) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

/**
 * Speech-to-Text engine interface.
 * Platform implementations:
 * - Android: Cloud-based (Groq API) - instant ready, no downloads
 * - Desktop: Offline (WhisperJNI) - requires model download
 */
interface SpeechToTextEngine {
    val state: StateFlow<TranscriptionState>

    /**
     * Initialize the engine.
     * - Cloud: Instant (validates API key)
     * - Desktop: May download model if needed
     */
    suspend fun initialize()

    /**
     * Transcribe audio data
     * @param audioData Raw PCM audio (16kHz, mono, 16-bit)
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult

    /**
     * Check if engine is ready to transcribe
     */
    fun isReady(): Boolean

    /**
     * Release resources
     */
    fun release()
}

class SpeechToTextException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Factory function - implemented per platform
 */
expect fun createSpeechToTextEngine(): SpeechToTextEngine