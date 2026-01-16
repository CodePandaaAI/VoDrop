package com.liftley.vodrop.settings

import com.liftley.vodrop.stt.WhisperModel

/**
 * Transcription mode for Pro users
 */
enum class TranscriptionMode {
    OFFLINE_BALANCED,   // Uses Balanced model offline
    OFFLINE_QUALITY,    // Uses Quality model offline (Pro only)
    CLOUD              // Uses Groq Whisper API (Pro only)
}

/**
 * User's transcription preferences
 */
data class TranscriptionSettings(
    val mode: TranscriptionMode = TranscriptionMode.OFFLINE_BALANCED,
    val autoImproveWithAI: Boolean = true,  // Auto-apply Gemini (Pro only)
    val qualityModelDownloaded: Boolean = false
)