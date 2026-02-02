package com.liftley.vodrop.data.llm

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JVM Text cleanup service using HTTP calls to Firebase Functions.
 * Calls cleanupText function directly via REST.
 */
class FirebaseTextCleanupService : TextCleanupService {

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "https://us-central1-post-3424f.cloudfunctions.net"

    override suspend fun cleanupText(rawText: String): Result<String> {
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }

        return try {
            val escapedText = rawText.replace("\"", "\\\"").replace("\n", "\\n")

            val response = httpClient.post("$baseUrl/cleanupText") {
                contentType(ContentType.Application.Json)
                setBody("""{"data":{"text":"$escapedText"}}""")
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val result = json.decodeFromString<FunctionResponse>(body)
                Result.success(result.result?.text ?: rawText)
            } else {
                Result.failure(Exception("Cleanup failed: ${response.status}"))
            }
        } catch (e: Exception) {
            println("[JvmCleanup] Error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean = true
}

@Serializable
private data class FunctionResponse(val result: FunctionResult? = null)

@Serializable
private data class FunctionResult(val text: String? = null)