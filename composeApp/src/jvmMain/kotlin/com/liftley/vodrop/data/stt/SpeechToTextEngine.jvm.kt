package com.liftley.vodrop.data.stt

import com.liftley.vodrop.data.audio.AudioConfig
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * JVM Speech-to-Text engine using Firebase Cloud Functions via HTTP.
 * For desktop - calls transcribe function directly.
 */
class JvmSpeechToTextEngine : SpeechToTextEngine {

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 540_000  // 9 minutes for long transcriptions
            connectTimeoutMillis = 30_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://us-central1-post-3424f.cloudfunctions.net"

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (audioData.isEmpty()) {
            return TranscriptionResult.Error("No audio data")
        }

        return withContext(Dispatchers.IO) {
            try {
                println("[JvmSTT] Transcribing ${audioData.size} bytes...")
                val wavData = createWavFile(audioData)
                val audioBase64 = Base64.getEncoder().encodeToString(wavData)

                val response = httpClient.post("$baseUrl/transcribe") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"data":{"audio":"$audioBase64"}}""")
                }

                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val result = json.decodeFromString<FunctionResponse>(body)
                    val text = result.result?.text ?: ""
                    println("[JvmSTT] Done: ${text.take(50)}...")
                    TranscriptionResult.Success(text)
                } else {
                    TranscriptionResult.Error("Transcription failed: ${response.status}")
                }
            } catch (e: Exception) {
                println("[JvmSTT] Error: ${e.message}")
                TranscriptionResult.Error("Failed: ${e.message}")
            }
        }
    }

    /**
     * Create WAV file from raw PCM.
     * Format: 16kHz, mono, 16-bit PCM
     */
    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val channels = AudioConfig.CHANNELS
        val bitsPerSample = AudioConfig.BITS_PER_SAMPLE
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size

        val buffer = ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}

@Serializable
private data class FunctionResponse(val result: FunctionResult? = null)

@Serializable
private data class FunctionResult(val text: String? = null)

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()