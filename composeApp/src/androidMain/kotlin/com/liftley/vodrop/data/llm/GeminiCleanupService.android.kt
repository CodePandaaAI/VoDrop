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
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "GeminiCleanup"

/**
 * Gemini-based text cleanup service.
 * Uses Gemini 2.0 Flash for fast, high-quality text cleanup.
 *
 * ⚡ OPTIMIZED: Reduced connection pool, proper timeouts
 */
class GeminiCleanupService(
    private val apiKey: String
) : TextCleanupService {

    // ⚡ OPTIMIZED: Lazy initialization with reduced connection pool
    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    retryOnConnectionFailure(false)
                    // Reduce idle connections to save battery
                    connectionPool(ConnectionPool(1, 15, TimeUnit.SECONDS))
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000    // 60 seconds
                connectTimeoutMillis = 15_000    // 15 seconds
                socketTimeoutMillis = 60_000     // 60 seconds
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
    private val model = "gemini-3-flash-preview"  // Updated to stable model

    companion object {
        private val CLEANUP_PROMPT = """
You are an expert transcription editor. Your job is to clean up speech-to-text output.

INPUT: Raw transcription that may contain recognition errors, filler words, and formatting issues.

YOUR TASKS:
1. PRESERVE THE ORIGINAL MESSAGE - Do not add, remove, or change the meaning
2. FIX GRAMMAR - Correct sentence structure, subject-verb agreement, tense consistency
3. FIX MISHEARD WORDS - Identify words that were incorrectly recognized by speech-to-text:
   - "Nebsoh" or "eebso" → "NEBOSH" (if context suggests a certification/safety/well Known words)
   - Look for words that sound similar but don't make sense in context
   - Common confusions: proper nouns, technical terms, acronyms
4. REMOVE FILLER WORDS - um, uh, ah, like, you know, basically, so, well, right, okay so
5. REMOVE STUTTERS - "I I think" → "I think", repeated phrases
6. FORMAT LISTS - If someone says "first... second... third..." or "point 1, point 2":
   - Format as bullet points or numbered list on new lines
   - Example: "first do this second do that" → 
     "1. Do this
      2. Do that"
7. CAPITALIZE PROPERLY:
   - Proper nouns (names, places, companies)
   - Acronyms (NEBOSH, NASA, API, etc.)
   - Start of sentences
8. PUNCTUATION - Add proper commas, periods, question marks
9. KEEP IT NATURAL - Don't make it sound robotic or overly formal. Match the speaker's tone.

IMPORTANT:
- Only fix what's clearly wrong
- If a word might be intentional, keep it
- Do NOT rewrite or paraphrase
- Return ONLY the cleaned text, no explanations

Transcription:
"""
    }

    override suspend fun cleanupText(rawText: String): Result<String> {
        if (rawText.isBlank()) {
            return Result.success(rawText)
        }

        if (rawText.length < 10) {
            return Result.success(rawText)
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = CLEANUP_PROMPT + "\"$rawText\""
                val requestBody = buildRequestBody(prompt)

                Log.d(LOG_TAG, "Sending cleanup request for ${rawText.length} chars")

                val response = httpClient.post("$baseUrl/$model:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                val responseText = response.bodyAsText()
                Log.d(LOG_TAG, "Response status: ${response.status}")

                if (response.status.isSuccess()) {
                    val cleanedText = parseResponse(responseText)
                    if (cleanedText != null) {
                        Log.d(LOG_TAG, "Cleanup successful: ${cleanedText.take(50)}...")
                        Result.success(cleanedText)
                    } else {
                        Log.w(LOG_TAG, "Failed to parse response, using original")
                        Result.success(rawText)
                    }
                } else {
                    Log.e(LOG_TAG, "API error: $responseText")
                    Result.failure(Exception("API error: ${response.status}"))
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Cleanup failed", e)
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
    "maxOutputTokens": 2048,
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
            Log.e(LOG_TAG, "Parse error", e)
            null
        }
    }

    override fun isAvailable(): Boolean {
        return apiKey.isNotBlank()
    }

    fun close() {
        try {
            httpClient.close()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error closing HTTP client", e)
        }
    }
}

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
private data class Candidate(
    val content: Content? = null
)

@Serializable
private data class Content(
    val parts: List<Part>? = null
)

@Serializable
private data class Part(
    val text: String? = null
)