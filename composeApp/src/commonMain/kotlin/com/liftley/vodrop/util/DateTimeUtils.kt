package com.liftley.vodrop.util

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Utility functions for date/time formatting
 */
object DateTimeUtils {

    /**
     * Format current timestamp for transcription labels
     * Example: "2026-01-16 18:30"
     */
    fun formatCurrentTimestamp(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.date} ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    /**
     * Format a relative time (e.g., "2 minutes ago", "1 hour ago")
     */
    fun formatRelativeTime(timestamp: String): String {
        // For now, just return the timestamp
        // Can be enhanced later with relative time logic
        return timestamp
    }
}