package com.liftley.vodrop.data.llm

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "GeminiCleanup"

/**
 * Gemini-based text cleanup service with style support
 */
class GeminiCleanupService(
    private val apiKey: String
) : TextCleanupService {

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
    private val model = "gemini-3-flash-preview"

    // ═══════════════════════════════════════════════════════════════
    // BASE PROMPT - Core cleanup rules (all styles inherit from this)
    // ═══════════════════════════════════════════════════════════════

    private val BASE_CLEANUP_RULES = """
You are an expert transcription editor. Your job is to clean up speech-to-text output.

INPUT: Raw transcription that may contain recognition errors, filler words, and formatting issues.

CORE EDITING RULES (ALWAYS APPLY):

1. PRESERVE THE ORIGINAL MESSAGE
   - Do not add, remove, or change the meaning
   - Keep the speaker's intent intact
   - If unsure, keep the original

2. FIX GRAMMAR
   - Correct sentence structure
   - Fix subject-verb agreement
   - Maintain tense consistency

3. FIX MISHEARD WORDS
   - Identify words incorrectly recognized by speech-to-text
   - "Nebsoh" or "eebso" → "NEBOSH" (if context suggests certification/safety)
   - Look for words that sound similar but don't make sense in context
   - Common confusions: proper nouns, technical terms, acronyms

4. REMOVE FILLER WORDS
   - Remove: um, uh, ah, like, you know, basically, so, well, right, okay so
   - Keep if intentional emphasis: "I was like, wow!"

5. REMOVE STUTTERS
   - "I I think" → "I think"
   - Remove repeated phrases and false starts

6. CAPITALIZE PROPERLY
   - Proper nouns (names, places, companies, brands)
   - Acronyms (NEBOSH, NASA, API, etc.)
   - Start of sentences

7. PUNCTUATION
   - Add proper commas, periods, question marks
   - Use appropriate punctuation for tone

FORMATTING RULES (IMPORTANT):

8. FORMAT LISTS & POINTS
   If someone mentions multiple items, steps, or points:
   - "first do this second do that third do this" → 
     "1. Do this
      2. Do that
      3. Do this"
   - Use numbered lists for sequences/steps
   - Use bullet points (• or -) for non-sequential items

9. ADD PARAGRAPH BREAKS
   - Add line breaks between different topics or ideas
   - If the speaker changes subject, start a new paragraph
   - Don't create one massive wall of text

10. STRUCTURE LONG CONTENT
    For longer transcriptions:
    - Break into logical sections
    - Add spacing between distinct thoughts
    - If there's a clear topic change, add a blank line

OUTPUT RULES:

IMPORTANT:
- Only fix what's clearly wrong
- If a word might be intentional, keep it
- Do NOT rewrite or paraphrase the content
- Return ONLY the cleaned text, no explanations or commentary
"""

    // ═══════════════════════════════════════════════════════════════
    // STYLE-SPECIFIC ADDITIONS
    // ═══════════════════════════════════════════════════════════════

    private val FORMAL_STYLE_ADDITION = """

STYLE: FORMAL & PROFESSIONAL:

Apply these ADDITIONAL adjustments for a professional tone:

- Use complete sentences (avoid fragments)
- Replace casual phrases with professional alternatives:
  - "gonna" → "going to"
  - "wanna" → "want to"
  - "gotta" → "have to" / "need to"
  - "kinda" → "somewhat" / "rather"
  - "yeah" → "yes"
  - "nope" → "no"
  - "stuff" → "items" / "materials" / "matters"
  - "things" → be more specific if possible
  - "got" → "received" / "obtained" / "have"
  - "a lot" → "many" / "numerous" / "significant"

- Use formal contractions sparingly (prefer "do not" over "don't" in formal contexts)
- Ensure professional vocabulary where appropriate
- Keep sentences clear and well-structured
- This is for business emails, presentations, and professional documents

BUT STILL:
- Keep the original meaning exactly the same
- Don't make it sound robotic or overly stiff
- Maintain natural flow
"""

    private val INFORMAL_STYLE_ADDITION = """

STYLE: INFORMAL & NATURAL

Keep the speaker's natural voice while cleaning up:

- Contractions are fine (don't, won't, can't, it's)
- Keep casual phrases if they sound natural
- Match the speaker's original energy and enthusiasm
- Light slang is acceptable if it fits the context
- Focus on clarity more than formality
- This is for personal notes, messages, and casual communication

GOAL: Clean and readable, but sounds like YOU wrote it, not a robot.
"""

    private val CASUAL_STYLE_ADDITION = """

STYLE: CASUAL & FRIENDLY

Keep it relaxed and approachable:

- Use contractions freely
- Keep friendly expressions and light slang
- Exclamation marks are welcome for enthusiasm!
- Casual transitions are fine ("So anyway...", "Oh, and...")
- Short sentences and fragments are okay for a punchy feel
- Emojis context is fine (if speaker seems excited, keep the energy)
- This is for quick notes, chat messages, and brainstorming

GOAL: Like texting a friend, but cleaned up and readable.
"""

    // ═══════════════════════════════════════════════════════════════
    // BUILD FINAL PROMPT
    // ═══════════════════════════════════════════════════════════════

    private fun buildPrompt(style: CleanupStyle): String {
        val styleAddition = when (style) {
            CleanupStyle.FORMAL -> FORMAL_STYLE_ADDITION
            CleanupStyle.INFORMAL -> INFORMAL_STYLE_ADDITION
            CleanupStyle.CASUAL -> CASUAL_STYLE_ADDITION
        }

        return "$BASE_CLEANUP_RULES$styleAddition\n\nTranscription:\n"
    }

    override suspend fun cleanupText(rawText: String, style: CleanupStyle): Result<String> {
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(style) + "\"$rawText\""
                val requestBody = buildRequestBody(prompt)

                Log.d(TAG, "Cleanup request: ${rawText.length} chars, style: ${style.name}")

                val response = httpClient.post("$baseUrl/$model:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                val responseText = response.bodyAsText()

                if (response.status.isSuccess()) {
                    val cleanedText = parseResponse(responseText)
                    if (cleanedText != null) {
                        Log.d(TAG, "Success: ${cleanedText.take(50)}...")
                        Result.success(cleanedText)
                    } else {
                        Log.w(TAG, "Empty response, returning original")
                        Result.success(rawText)
                    }
                } else {
                    Log.e(TAG, "API error: ${response.status} - $responseText")
                    Result.failure(Exception("API error: ${response.status}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
                Result.failure(e)
            }
        }
    }

    private fun buildRequestBody(prompt: String): String {
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """
{
  "contents": [{
    "parts": [{
      "text": "$escapedPrompt"
    }]
  }],
  "generationConfig": {
    "temperature": 0.1,
    "maxOutputTokens": 4096,
    "topP": 0.8,
    "topK": 10
  }
}
        """.trimIndent()
    }

    private fun parseResponse(responseText: String): String? {
        return try {
            val response = json.decodeFromString<GeminiResponse>(responseText)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    fun close() {
        httpClient.close()
    }
}

@Serializable
private data class GeminiResponse(val candidates: List<Candidate>? = null)

@Serializable
private data class Candidate(val content: Content? = null)

@Serializable
private data class Content(val parts: List<Part>? = null)

@Serializable
private data class Part(val text: String? = null)