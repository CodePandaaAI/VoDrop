package com.liftley.vodrop.data.stt

import com.liftley.vodrop.data.audio.AudioConfig
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM Speech-to-Text engine using Firebase Cloud Functions.
 */
class JvmSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val firebaseFunctions: FirebaseFunctionsService by inject()

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (audioData.isEmpty()) {
            return TranscriptionResult.Error("No audio data")
        }

        return withContext(Dispatchers.IO) {
            try {
                println("[JvmSTT] Transcribing ${audioData.size} bytes (48kHz)...")
                val wavData = createWavFile(audioData)
                val result = firebaseFunctions.transcribe(wavData)

                result.fold(
                    onSuccess = { text ->
                        println("[JvmSTT] Done: ${text.take(50)}...")
                        TranscriptionResult.Success(RuleBasedTextCleanup.cleanup(text))
                    },
                    onFailure = { error ->
                        TranscriptionResult.Error(error.message ?: "Failed")
                    }
                )
            } catch (e: Exception) {
                TranscriptionResult.Error("Failed: ${e.message}")
            }
        }
    }

    /**
     * Create WAV file from raw PCM using ByteBuffer.
     * Format: 48kHz, mono, 16-bit PCM
     */
    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = AudioConfig.SAMPLE_RATE        // 48000
        val channels = AudioConfig.CHANNELS              // 1
        val bitsPerSample = AudioConfig.BITS_PER_SAMPLE  // 16
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

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()