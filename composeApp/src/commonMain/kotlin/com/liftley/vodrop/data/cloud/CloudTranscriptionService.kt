package com.liftley.vodrop.data.cloud

/**
 * Result of a transcription operation.
 */
sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

/**
 * Unified cloud transcription service.
 * 
 * Handles both:
 * - Speech-to-Text (Chirp 3)
 * - AI Polish (Gemini 3 Flash)
 * 
 * Single Firebase initialization, clean API.
 */
interface CloudTranscriptionService {
    
    /**
     * Transcribe audio to text.
     * @param audioData Raw PCM audio (16kHz, mono, 16-bit)
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    
    /**
     * Polish/cleanup text using AI.
     * Fixes grammar, removes filler words.
     * @return Polished text or null if failed
     */
    suspend fun polish(text: String): String?
}

/**
 * Platform-specific factory.
 */
expect fun createCloudTranscriptionService(): CloudTranscriptionService
