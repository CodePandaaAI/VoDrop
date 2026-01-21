package com.liftley.vodrop.data.stt

import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM Speech-to-Text engine using Firebase Cloud Functions.
 * Cloud-only - no offline capability.
 */
class JvmSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.NotReady)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val firebaseFunctions: FirebaseFunctionsService by inject()

    override suspend fun initialize() {
        println("[JvmSTT] Cloud engine initialized")
        _state.value = TranscriptionState.Ready
    }

    override fun isReady(): Boolean = _state.value is TranscriptionState.Ready

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (audioData.isEmpty()) {
            return TranscriptionResult.Error("No audio data provided")
        }

        return withContext(Dispatchers.IO) {
            try {
                _state.value = TranscriptionState.Transcribing
                println("[JvmSTT] Sending ${audioData.size} bytes to Firebase...")

                val wavData = createWavFile(audioData)
                val result = firebaseFunctions.transcribe(wavData)

                _state.value = TranscriptionState.Ready

                result.fold(
                    onSuccess = { text ->
                        println("[JvmSTT] Transcription complete: ${text.take(50)}...")
                        val cleanedText = RuleBasedTextCleanup.cleanup(text)
                        TranscriptionResult.Success(cleanedText, 0L)
                    },
                    onFailure = { error ->
                        println("[JvmSTT] Error: ${error.message}")
                        TranscriptionResult.Error(error.message ?: "Transcription failed")
                    }
                )
            } catch (e: Exception) {
                println("[JvmSTT] Exception: ${e.message}")
                _state.value = TranscriptionState.Error(e.message ?: "Unknown error")
                TranscriptionResult.Error("Transcription failed: ${e.message}")
            }
        }
    }

    override fun release() {
        println("[JvmSTT] Releasing resources")
        _state.value = TranscriptionState.NotReady
    }

    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + pcmData.size).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1 size
        buffer.putShort(1) // Audio format (PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()