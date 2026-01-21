package com.liftley.vodrop.data.stt

/**
 * Local text cleanup using regex patterns.
 * Fast, offline processing for basic cleanup.
 */
object RuleBasedTextCleanup {

    // Patterns
    private val WHITESPACE = Regex("\\s+")
    private val REPEATED_WORD = Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE)
    private val SENTENCE_CAPITALIZE = Regex("([.!?])\\s+([a-z])")
    private val STANDALONE_I = Regex("\\bi\\b")

    private val FILLERS = listOf(
        Regex("\\bum+\\b", RegexOption.IGNORE_CASE),
        Regex("\\buh+\\b", RegexOption.IGNORE_CASE),
        Regex("\\bah+\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmm+\\b", RegexOption.IGNORE_CASE),
        Regex("\\blike\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\b(you know)\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bbasically\\b(?=\\s*,)", RegexOption.IGNORE_CASE)
    )

    private val CORRECTIONS = listOf(
        Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
        Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
        Regex("\\bgotta\\b", RegexOption.IGNORE_CASE) to "got to"
    )

    /**
     * Clean transcription text with basic rules.
     */
    fun cleanup(text: String): String {
        if (text.isBlank()) return text

        var result = text.trim()
            .replace(WHITESPACE, " ")

        // Remove fillers
        FILLERS.forEach { result = result.replace(it, "") }

        // Fix stutters
        result = result.replace(REPEATED_WORD) { it.groupValues[1] }

        // Apply corrections
        CORRECTIONS.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }

        // Cleanup punctuation
        result = result
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("^\\s*,\\s*"), "")
            .replace(WHITESPACE, " ")
            .trim()

        // Capitalize
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { it.uppercaseChar() }
            result = result.replace(SENTENCE_CAPITALIZE) { "${it.groupValues[1]} ${it.groupValues[2].uppercase()}" }
            result = result.replace(STANDALONE_I, "I")
        }

        // Add ending punctuation
        if (result.isNotEmpty() && !result.last().toString().matches(Regex("[.!?]"))) {
            result = "$result."
        }

        return result
    }
}