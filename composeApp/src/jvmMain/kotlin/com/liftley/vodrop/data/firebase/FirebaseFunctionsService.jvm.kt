package com.liftley.vodrop.data.firebase

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * JVM implementation of FirebaseFunctionsService using HTTP.
 * Since Firebase SDK is Android-only, we call the functions via REST.
 *
 * NOTE: For JVM/Desktop, we bypass auth check since desktop has no Firebase Auth.
 * The Cloud Function requires auth, so desktop transcription is disabled for now.
 *
 * TODO: For production, either:
 * 1. Create separate unauthenticated endpoints for desktop
 * 2. Implement Firebase Auth REST API for desktop
 */
class JvmFirebaseFunctionsService : FirebaseFunctionsService {

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Your Firebase project region + project ID
    // Format: https://{region}-{projectId}.cloudfunctions.net/{functionName}
    private val baseUrl = "https://us-central1-post-3424f.cloudfunctions.net"

    override suspend fun transcribe(audioData: ByteArray): Result<String> {
        return try {
            val audioBase64 = Base64.getEncoder().encodeToString(audioData)

            val response = httpClient.post("$baseUrl/transcribe") {
                contentType(ContentType.Application.Json)
                setBody("""{"data":{"audio":"$audioBase64"}}""")
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val result = json.decodeFromString<FunctionResponse>(body)
                Result.success(result.result?.text ?: "")
            } else {
                Result.failure(Exception("Transcription failed: ${response.status}"))
            }
        } catch (e: Exception) {
            println("[JvmFirebase] Transcribe error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun cleanupText(text: String, style: String): Result<String> {
        return try {
            val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")

            val response = httpClient.post("$baseUrl/cleanupText") {
                contentType(ContentType.Application.Json)
                setBody("""{"data":{"text":"$escapedText","style":"$style"}}""")
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val result = json.decodeFromString<FunctionResponse>(body)
                Result.success(result.result?.text ?: text)
            } else {
                Result.failure(Exception("Cleanup failed: ${response.status}"))
            }
        } catch (e: Exception) {
            println("[JvmFirebase] Cleanup error: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        httpClient.close()
    }
}

@Serializable
private data class FunctionResponse(val result: FunctionResult? = null)

@Serializable
private data class FunctionResult(val text: String? = null)