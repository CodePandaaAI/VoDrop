package com.liftley.vodrop.data.stt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.liftley.vodrop.data.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "CloudSTT"

/**
 * Android Speech-to-Text engine using Firebase Cloud Functions.
 * Uses putFile for memory-safe uploads of any recording length.
 */
class CloudSpeechToTextEngine(): SpeechToTextEngine, KoinComponent {

    private val context: Context by inject()

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance("gs://post-3424f.firebasestorage.app") }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // 1. Write WAV to temp file (memory-safe)
                tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
                writeWavToFile(audioData, tempFile)

                val filename = "${UUID.randomUUID()}.wav"
                val storageRef = storage.reference.child("uploads/$filename")

                // 2. Upload using putFile (streams from disk, low RAM usage)
                Log.d(TAG, "Uploading ${tempFile.length()} bytes (${AudioConfig.SAMPLE_RATE}Hz WAV)...")
                storageRef.putFile(Uri.fromFile(tempFile)).await()

                // 3. Get GCS URI
                val gcsUri = "gs://${storageRef.bucket}/uploads/$filename"

                // 4. Call Cloud Function
                Log.d(TAG, "Transcribing...")
                val result = functions
                    .getHttpsCallable("transcribeChirp")
                    .apply { setTimeout(540, java.util.concurrent.TimeUnit.SECONDS) }
                    .call(mapOf("gcsUri" to gcsUri))
                    .await()

                // 5. Cleanup remote file
                storageRef.delete().addOnFailureListener { Log.w(TAG, "Cleanup failed", it) }

                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val text = response?.get("text") as? String ?: ""

                TranscriptionResult.Success(text)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error(e.message ?: "Transcription failed")
            } finally {
                // 6. Always delete local temp file
                tempFile?.delete()
            }
        }
    }

    /**
     * Write WAV file to disk (header + PCM data).
     */
    private fun writeWavToFile(pcmData: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use { stream ->
            stream.write(createWavHeader(pcmData.size))
            stream.write(pcmData)
        }
    }

    /**
     * Create 44-byte WAV header for PCM data.
     */
    private fun createWavHeader(dataSize: Int): ByteArray {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val channels = AudioConfig.CHANNELS
        val bitsPerSample = AudioConfig.BITS_PER_SAMPLE
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        return ByteBuffer.allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }
            .array()
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = CloudSpeechToTextEngine()