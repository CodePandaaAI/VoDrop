package com.liftley.vodrop.data.stt

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.liftley.vodrop.data.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "CloudSTT"

/**
 * Android Speech-to-Text engine using Firebase Cloud Functions.
 */
class CloudSpeechToTextEngine : SpeechToTextEngine {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance("gs://post-3424f.firebasestorage.app") }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Create WAV file (48kHz, mono, 16-bit)
                val wavData = createWavFile(audioData)
                val filename = "${UUID.randomUUID()}.wav"
                val storageRef = storage.reference.child("uploads/$filename")

                // 2. Upload to Firebase Storage
                Log.d(TAG, "Uploading ${wavData.size} bytes (48kHz WAV)...")
                storageRef.putBytes(wavData).await()

                // 3. Get GCS URI
                val gcsUri = "gs://${storageRef.bucket}/uploads/$filename"

                // 4. Call Cloud Function
                Log.d(TAG, "Transcribing...")
                val result = functions
                    .getHttpsCallable("transcribeChirp")
                    .apply { setTimeout(540, java.util.concurrent.TimeUnit.SECONDS) }
                    .call(mapOf("gcsUri" to gcsUri))
                    .await()

                // 5. Cleanup
                storageRef.delete().addOnFailureListener { Log.w(TAG, "Cleanup failed", it) }

                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val text = response?.get("text") as? String ?: ""

                TranscriptionResult.Success(text)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error(e.message ?: "Transcription failed")
            }
        }
    }

    /**
     * Create WAV file from raw PCM using ByteBuffer (correct byte ordering).
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
        buffer.putInt(36 + dataSize)  // File size - 8
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)                          // Chunk size
        buffer.putShort(1)                         // Audio format (1 = PCM)
        buffer.putShort(channels.toShort())        // Channels
        buffer.putInt(sampleRate)                  // Sample rate
        buffer.putInt(byteRate)                    // Byte rate
        buffer.putShort(blockAlign.toShort())      // Block align
        buffer.putShort(bitsPerSample.toShort())   // Bits per sample

        // data chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = CloudSpeechToTextEngine()