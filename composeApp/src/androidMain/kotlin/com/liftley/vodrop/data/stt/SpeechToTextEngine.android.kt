package com.liftley.vodrop.data.stt

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.liftley.vodrop.data.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

private const val TAG = "CloudSTTEngine"

/**
 * Android Speech-to-Text engine using Firebase Cloud Functions.
 */
class CloudSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.NotReady)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val storage: FirebaseStorage by inject()

    override suspend fun initialize() {
        _state.value = TranscriptionState.Ready
    }

    override fun isReady(): Boolean = _state.value is TranscriptionState.Ready

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                _state.value = TranscriptionState.Transcribing

                // 1. Create WAV Data
                val wavData = createWavFile(audioData)
                val filename = "${UUID.randomUUID()}.wav"
                val storageRef = storage.reference.child("uploads/$filename")

                // 2. Upload to Firebase Storage (Robust on slow internet)
                Log.d(TAG, "Uploading ${wavData.size} bytes to Storage...")
                storageRef.putBytes(wavData).await()

                // 3. Get the 'gs://' URI (Internal Google Path)
                // Note: We reconstruct it manually to save an API call, or you can get it from metadata
                val gcsUri = "gs://${storageRef.bucket}/uploads/$filename"

                // 4. Call Cloud Function with the URI (Lightweight request)
                Log.d(TAG, "Calling transcribe function with URI: $gcsUri")
                val result = functions
                    .getHttpsCallable("transcribeChirp") // New function name
                    .apply { setTimeout(540, java.util.concurrent.TimeUnit.SECONDS) }
                    .call(mapOf("gcsUri" to gcsUri))
                    .await()

                // 5. Cleanup: Delete file after success (Optional, or use Lifecycle policy in Console)
                storageRef.delete().addOnFailureListener { Log.w(TAG, "Failed to cleanup audio", it) }

                _state.value = TranscriptionState.Ready

                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val text = response?.get("text") as? String ?: ""

                TranscriptionResult.Success(text, 0L)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _state.value = TranscriptionState.Error(e.message ?: "Unknown error")
                TranscriptionResult.Error("Failed: ${e.message}")
            }
        }
    }

    override fun release() {
        _state.value = TranscriptionState.NotReady
    }

    /**
     * Create WAV file from raw PCM using global AudioConfig
     */
    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val channels = AudioConfig.CHANNELS
        val bitsPerSample = AudioConfig.BITS_PER_SAMPLE
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
        header[24] = (0).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = (0).toByte()
        header[27] = (0).toByte()
        header[28] = (0).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = (0).toByte()
        header[31] = (0).toByte()
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