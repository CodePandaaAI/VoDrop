package com.liftley.vodrop.data.llm

/**
 * Interface for LLM-based text cleanup.
 * Can be implemented with different providers (Gemini, OpenAI, etc.)
 */
interface TextCleanupService {
    /**
     * Clean up text using the specified style
     */
    suspend fun cleanupText(rawText: String, style: CleanupStyle = CleanupStyle.DEFAULT): Result<String>

    /**
     * Check if the service is available
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