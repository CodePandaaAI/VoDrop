package com.liftley.vodrop.data.llm

/** LLM-based text cleanup service */
interface TextCleanupService {
    suspend fun cleanupText(rawText: String): Result<String>
    fun isAvailable(): Boolean
}