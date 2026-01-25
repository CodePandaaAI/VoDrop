package com.liftley.vodrop.data.stt

/**
 * Result of a transcription operation
 */
sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

/**
 * Speech-to-Text engine interface.
 * Cloud-only - no initialization needed.
 */
interface SpeechToTextEngine {
    /**
     * Transcribe audio data
     * @param audioData Raw PCM audio (16kHz, mono, 16-bit)
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
}

/**
 * Factory function - implemented per platform
 */
expect fun createSpeechToTextEngine(): SpeechToTextEngine