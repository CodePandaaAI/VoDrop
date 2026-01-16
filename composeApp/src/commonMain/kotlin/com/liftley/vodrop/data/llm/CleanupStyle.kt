package com.liftley.vodrop.data.llm

/**
 * Text cleanup styles that affect how Gemini processes transcriptions.
 * Each style inherits the base cleanup rules but adds its own characteristics.
 */
enum class CleanupStyle(
    val displayName: String,
    val emoji: String,
    val description: String,
    val example: String
) {
    FORMAL(
        displayName = "Formal",
        emoji = "👔",
        description = "Professional & polished. Great for work, emails, presentations.",
        example = "\"I would like to schedule a meeting to discuss the project requirements.\""
    ),
    INFORMAL(
        displayName = "Informal",
        emoji = "💬",
        description = "Clean & natural. Fixes errors while keeping your voice.",
        example = "\"Let's set up a meeting to talk about the project.\""
    ),
    CASUAL(
        displayName = "Casual",
        emoji = "😊",
        description = "Friendly & relaxed. Perfect for notes, messages, ideas.",
        example = "\"Hey, let's chat about the project sometime!\""
    );

    companion object {
        val DEFAULT = INFORMAL

        fun fromName(name: String): CleanupStyle {
            return entries.find { it.name == name } ?: DEFAULT
        }
    }
}