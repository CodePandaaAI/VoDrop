package com.liftley.vodrop.data.llm

/**
 * Interface for LLM-based text cleanup.
 * Can be implemented with different providers (Gemini, OpenAI, etc.)
 */
interface TextCleanupService {
    /**
     * Clean up transcribed text using LLM.
     * @param rawText The raw transcription from Whisper
     * @return Cleaned text, or null if cleanup failed
     */
    suspend fun cleanupText(rawText: String): Result<String>

    /**
     * Check if the service is available (has API key, network, etc.)
     */
    fun isAvailable(): Boolean
}

/**
 * Result wrapper for cleanup operations
 */
sealed class CleanupResult {
    data class Success(val cleanedText: String) : CleanupResult()
    data class Error(val message: String) : CleanupResult()
    data object Unavailable : CleanupResult()
}