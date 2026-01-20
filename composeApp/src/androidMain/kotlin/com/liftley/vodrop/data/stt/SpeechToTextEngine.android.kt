package com.liftley.vodrop.data.stt

import android.util.Log
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "CloudSTTEngine"

/**
 * Android Speech-to-Text engine using Firebase Cloud Functions.
 *
 * Benefits:
 * - API keys secured on server
 * - No hardcoded credentials in app
 * - Firebase handles auth automatically
 */
class CloudSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.NotReady)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val firebaseFunctions: FirebaseFunctionsService by inject()

    override suspend fun initialize() {
        Log.d(TAG, "Cloud engine initialized (Firebase)")
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
                Log.d(TAG, "Sending ${audioData.size} bytes to Firebase...")

                // Create WAV from raw PCM
                val wavData = createWavFile(audioData)

                val result = firebaseFunctions.transcribe(wavData)

                _state.value = TranscriptionState.Ready

                result.fold(
                    onSuccess = { text ->
                        Log.d(TAG, "Transcription complete: ${text.take(50)}...")
                        val cleanedText = RuleBasedTextCleanup.cleanup(text)
                        TranscriptionResult.Success(cleanedText, 0L)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Transcription error: ${error.message}")
                        TranscriptionResult.Error(error.message ?: "Transcription failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Transcription exception", e)
                _state.value = TranscriptionState.Error(e.message ?: "Unknown error")
                TranscriptionResult.Error("Transcription failed: ${e.message}")
            }
        }
    }

    override fun release() {
        Log.d(TAG, "Releasing cloud engine resources")
        _state.value = TranscriptionState.NotReady
    }

    /**
     * Create WAV file from raw PCM (16kHz, mono, 16-bit)
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
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()

        // File size
        header[4] = (fileSize and 0xff).toByte()
        header[5] = ((fileSize shr 8) and 0xff).toByte()
        header[6] = ((fileSize shr 16) and 0xff).toByte()
        header[7] = ((fileSize shr 24) and 0xff).toByte()

        // WAVE
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = blockAlign.toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        return header + pcmData
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = CloudSpeechToTextEngine()