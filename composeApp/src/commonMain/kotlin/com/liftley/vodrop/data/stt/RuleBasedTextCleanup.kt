package com.liftley.vodrop.data.stt

/**
 * Rule-based text cleanup for STT output.
 * Uses pre-compiled regex patterns for fast processing.
 * This runs locally without network calls.
 */
object RuleBasedTextCleanup {

    /**
     * Clean up raw transcription text using rule-based patterns.
     * Fast and works offline.
     */
    fun cleanup(text: String): String {
        if (text.isBlank()) return text

        var result = text.trim()

        // Step 1: Normalize whitespace
        result = result.replace(TextCleanupRules.WHITESPACE, " ")

        // Step 2: Remove filler words
        for (pattern in TextCleanupRules.FILLER_PATTERNS) {
            result = result.replace(pattern, "")
        }

        // Step 3: Remove repeated words (stuttering)
        result = result.replace(TextCleanupRules.REPEATED_WORD) { match ->
            match.groupValues[1]
        }

        // Step 4: Remove repeated phrases
        result = result.replace(TextCleanupRules.REPEATED_PHRASE_2) { match ->
            match.groupValues[1]
        }
        result = result.replace(TextCleanupRules.REPEATED_PHRASE_3) { match ->
            match.groupValues[1]
        }

        // Step 5: Fix common STT errors
        for ((pattern, replacement) in TextCleanupRules.CORRECTIONS) {
            result = result.replace(pattern, replacement)
        }

        // Step 6: Clean up punctuation
        result = result.replace(TextCleanupRules.MULTIPLE_COMMAS, ",")
        result = result.replace(TextCleanupRules.COMMA_SPACING, ", ")
        result = result.replace(TextCleanupRules.WHITESPACE, " ")
        result = result.replace(TextCleanupRules.COMMA_AT_START, "")
        result = result.replace(TextCleanupRules.PERIOD_COMMA, ".")

        // Step 7: Capitalize properly
        result = result.trim()
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { it.uppercaseChar() }
        }
        result = result.replace(TextCleanupRules.SENTENCE_CAPITALIZE) { match ->
            "${match.groupValues[1]} ${match.groupValues[2].uppercase()}"
        }
        result = result.replace(TextCleanupRules.STANDALONE_I, "I")

        // Step 8: Add ending punctuation if missing
        result = result.trim()
        if (result.isNotEmpty() && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result = "$result."
        }

        // Final cleanup
        return result.replace(TextCleanupRules.WHITESPACE, " ").trim()
    }
}