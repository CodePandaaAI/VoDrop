package com.liftley.vodrop.domain.model

/**
 * **Transcription Entity**
 * 
 * Core domain model representing a saved voice note.
 * Designed to support the "Dual View" feature (Raw vs Polished).
 * 
 * @property id Unique identifier (Database ID).
 * @property timestamp ISO timestamp string.
 * @property originalText The raw output from the Speech-to-Text engine (Chirp).
 * @property polishedText The optional AI-enhanced version (Gemini). If null, AI polish was skipped or failed.
 */
data class Transcription(
    val id: Long = 0L,
    val timestamp: String,
    val originalText: String,
    val polishedText: String? = null
) {
    /** 
     * Helper to check if this entry has an AI-improved version available.
     * Used by UI to toggle visibility of the "Show Original" switch.
     */
    val hasPolished: Boolean get() = polishedText != null
}