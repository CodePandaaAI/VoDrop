package com.liftley.vodrop.domain.model

/**
 * Domain model representing a transcription entry.
 */
data class Transcription(
    val id: Long = 0L,
    val timestamp: String,
    val text: String
)