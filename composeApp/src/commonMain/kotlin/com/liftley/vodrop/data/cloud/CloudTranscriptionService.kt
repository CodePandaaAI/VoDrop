package com.liftley.vodrop.data.cloud

/**
 * **Transcription Result Wrapper**
 */
sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

/**
 * **Cloud Service Interface**
 * 
 * Defines the contract for Server-Side AI operations.
 * 
 * **Responsibilities:**
 * 1. [transcribe]: Audio -> Text (Google Chirp 3).
 * 2. [polish]: Text -> Better Text (Gemini 3 Flash).
 * 
 * Implementations (Android/Desktop) handle the networking/Firebase specifics.
 */
interface CloudTranscriptionService {
    
    /**
     * Uploads audio and waits for transcription.
     * Handles upload -> cloud function call -> result parsing.
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    
    /**
     * Sends text to LLM for cleanup.
     * Should fail gracefully (return null) if network issues occur.
     */
    suspend fun polish(text: String): String?
}

/**
 * Expect/Actual factory to provide platform-specific implementation.
 * (e.g., Android uses Firebase SDK, Desktop uses REST API).
 */
expect fun createCloudTranscriptionService(): CloudTranscriptionService
