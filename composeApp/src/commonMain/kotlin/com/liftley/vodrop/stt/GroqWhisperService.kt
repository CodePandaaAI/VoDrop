package com.liftley.vodrop.stt

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Groq Whisper API Service for cloud transcription
 * Uses Whisper Large v3 model for 95%+ accuracy
 */
class GroqWhisperService(
    private val apiKey: String,
    private val httpClient: HttpClient
) {
    companion object {
        private const val API_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3"
    }

    @Serializable
    private data class TranscriptionResponse(
        val text: String
    )

    @Serializable
    private data class ErrorResponse(
        val error: ErrorDetail? = null
    )

    @Serializable
    private data class ErrorDetail(
        val message: String? = null,
        val type: String? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Transcribe audio using Groq's Whisper Large v3
     */
    suspend fun transcribe(
        audioData: ByteArray,
        language: String = "en"
    ): TranscriptionResult {
        if (audioData.isEmpty()) {
            return TranscriptionResult.Error("No audio data provided")
        }

        return try {
            val startTime = Clock.System.now().toEpochMilliseconds()

            // Create WAV header for the raw PCM data
            val wavData = createWavFile(audioData)

            val response = httpClient.submitFormWithBinaryData(
                url = API_URL,
                formData = formData {
                    append("file", wavData, Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                    })
                    append("model", MODEL)
                    append("language", language)
                    append("response_format", "json")
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }

            val responseText = response.bodyAsText()
            val durationMs = Clock.System.now().toEpochMilliseconds() - startTime

            if (response.status.isSuccess()) {
                val result = json.decodeFromString<TranscriptionResponse>(responseText)
                TranscriptionResult.Success(
                    text = result.text.trim(),
                    durationMs = durationMs
                )
            } else {
                val error = try {
                    json.decodeFromString<ErrorResponse>(responseText)
                } catch (e: Exception) {
                    null
                }
                TranscriptionResult.Error(
                    error?.error?.message ?: "API error: ${response.status.value}"
                )
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Create a WAV file from raw PCM data
     * Format: 16kHz, mono, 16-bit PCM
     */
    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size - 8
        header[4] = (fileSize and 0xff).toByte()
        header[5] = ((fileSize shr 8) and 0xff).toByte()
        header[6] = ((fileSize shr 16) and 0xff).toByte()
        header[7] = ((fileSize shr 24) and 0xff).toByte()

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1 size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // Audio format (1 for PCM)
        header[20] = 1
        header[21] = 0

        // Num channels
        header[22] = channels.toByte()
        header[23] = 0

        // Sample rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // Byte rate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // Block align
        header[32] = blockAlign.toByte()
        header[33] = 0

        // Bits per sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Data size
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        return header + pcmData
    }

    /**
     * Check if service is available
     */
    fun isAvailable(): Boolean = apiKey.isNotBlank()
}