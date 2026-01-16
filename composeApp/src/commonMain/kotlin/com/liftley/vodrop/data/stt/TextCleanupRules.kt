package com.liftley.vodrop.data.stt

/**
 * Pre-compiled regex patterns for text cleanup.
 * Compiled once at class load time for performance.
 */
object TextCleanupRules {

    // Whitespace normalization
    val WHITESPACE = Regex("\\s+")

    // Filler words (spoken disfluencies)
    val FILLER_PATTERNS = listOf(
        Regex("\\bum+\\b", RegexOption.IGNORE_CASE),
        Regex("\\buh+\\b", RegexOption.IGNORE_CASE),
        Regex("\\bah+\\b", RegexOption.IGNORE_CASE),
        Regex("\\beh+\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmm+\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhm+\\b", RegexOption.IGNORE_CASE),
        Regex("\\ber+\\b", RegexOption.IGNORE_CASE),
        Regex("\\blike\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\b(you know)\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\b(i mean)\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bso+\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bwell\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bbasically\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bactually\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
        Regex("\\bright\\b(?=\\s*[,?])", RegexOption.IGNORE_CASE),
        Regex("\\bokay so\\b", RegexOption.IGNORE_CASE),
        Regex("\\byeah so\\b", RegexOption.IGNORE_CASE)
    )

    // Repeated words/phrases (stuttering)
    val REPEATED_WORD = Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE)
    val REPEATED_PHRASE_2 = Regex("\\b(\\w+\\s+\\w+),?\\s+\\1\\b", RegexOption.IGNORE_CASE)
    val REPEATED_PHRASE_3 = Regex("\\b(\\w+\\s+\\w+\\s+\\w+),?\\s+\\1\\b", RegexOption.IGNORE_CASE)

    // Common STT corrections (informal → formal)
    val CORRECTIONS = listOf(
        Regex("\\bu\\b", RegexOption.IGNORE_CASE) to "you",
        Regex("\\bur\\b", RegexOption.IGNORE_CASE) to "your",
        Regex("\\br\\b", RegexOption.IGNORE_CASE) to "are",
        Regex("\\bcuz\\b", RegexOption.IGNORE_CASE) to "because",
        Regex("\\bcause\\b", RegexOption.IGNORE_CASE) to "because",
        Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
        Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
        Regex("\\bgotta\\b", RegexOption.IGNORE_CASE) to "got to",
        Regex("\\bkinda\\b", RegexOption.IGNORE_CASE) to "kind of",
        Regex("\\bsorta\\b", RegexOption.IGNORE_CASE) to "sort of",
        Regex("\\bdunno\\b", RegexOption.IGNORE_CASE) to "don't know",
        Regex("\\blemme\\b", RegexOption.IGNORE_CASE) to "let me",
        Regex("\\bgimme\\b", RegexOption.IGNORE_CASE) to "give me"
    )

    // Punctuation cleanup
    val MULTIPLE_COMMAS = Regex(",\\s*,")
    val COMMA_SPACING = Regex("\\s*,\\s*")
    val COMMA_AT_START = Regex("^\\s*,\\s*")
    val PERIOD_COMMA = Regex("\\.\\s*,")
    val SENTENCE_CAPITALIZE = Regex("([.!?])\\s+([a-z])")
    val STANDALONE_I = Regex("\\bi\\b")
}