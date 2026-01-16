package com.liftley.vodrop.llm

/**
 * Configuration for LLM services.
 * In production, these should come from secure storage or backend.
 */
object LLMConfig {
    // Gemini API Key - MOVE TO SECURE STORAGE FOR PRODUCTION!
    const val GEMINI_API_KEY = "AIzaSyA0FJZGMqqgmmAj6NoxDTvCPG-Kg_gEebs"

    // Feature flags
    var isLLMCleanupEnabled = true  // Toggle for Pro users

    // Rate limits
    const val MAX_DAILY_CLEANUPS = 100
    const val MAX_TEXT_LENGTH = 5000
}