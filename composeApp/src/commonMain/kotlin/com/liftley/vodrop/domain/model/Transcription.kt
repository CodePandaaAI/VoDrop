package com.liftley.vodrop.domain.model

/**
 * Domain model representing a transcription entry.
 * Stores both original STT output and optional AI-polished version.
 */
data class Transcription(
    val id: Long = 0L,
    val timestamp: String,
    val originalText: String,
    val polishedText: String? = null
) {
    /** Whether AI polish has been applied */
    val hasPolished: Boolean get() = polishedText != null
}