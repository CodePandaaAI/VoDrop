package com.liftley.vodrop.llm

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini-based text cleanup service for Desktop.
 */
class GeminiCleanupService(
    private val apiKey: String
) : TextCleanupService {

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
    private val model = "gemini-3-flash-preview"

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
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = CLEANUP_PROMPT + "\"$rawText\""
                val requestBody = buildRequestBody(prompt)

                println("[GeminiCleanup] Sending request for ${rawText.length} chars")

                val response = httpClient.post("$baseUrl/$model:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                val responseText = response.bodyAsText()

                if (response.status.isSuccess()) {
                    val cleanedText = parseResponse(responseText)
                    if (cleanedText != null) {
                        println("[GeminiCleanup] Success: ${cleanedText.take(50)}...")
                        Result.success(cleanedText)
                    } else {
                        Result.success(rawText)
                    }
                } else {
                    println("[GeminiCleanup] Error: $responseText")
                    Result.failure(Exception("API error: ${response.status}"))
                }
            } catch (e: Exception) {
                println("[GeminiCleanup] Failed: ${e.message}")
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