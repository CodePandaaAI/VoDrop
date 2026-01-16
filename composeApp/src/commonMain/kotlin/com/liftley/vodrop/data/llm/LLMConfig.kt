package com.liftley.vodrop.data.llm

/**
 * Configuration for LLM services.
 * In production, these should come from secure storage or backend.
 */
object LLMConfig {
    // Gemini API Key - MOVE TO SECURE STORAGE FOR PRODUCTION!
    const val GEMINI_API_KEY = "AIzaSyA0FJZGMqqgmmAj6NoxDTvCPG-Kg_gEebs"

    // Feature flags - Disabled here, controlled by MainViewModel toggle now
    var isLLMCleanupEnabled = false

    // Rate limits
    const val MAX_DAILY_CLEANUPS = 100
    const val MAX_TEXT_LENGTH = 5000
}