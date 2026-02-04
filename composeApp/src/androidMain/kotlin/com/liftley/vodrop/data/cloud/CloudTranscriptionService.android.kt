package com.liftley.vodrop.data.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
import java.util.concurrent.TimeUnit

private const val TAG = "CloudTranscription"

/**
 * Android implementation of CloudTranscriptionService.
 * 
 * Single class for all cloud operations:
 * - Transcription via Chirp 3 (Firebase Function)
 * - AI Polish via Gemini (Firebase Function)
 * 
 * Uses putFile for memory-safe uploads of long recordings.
 */
class AndroidCloudTranscriptionService : CloudTranscriptionService, KoinComponent {

    private val context: Context by inject()

    // Lazy Firebase initialization - shared across all operations
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val storage: FirebaseStorage by lazy { 
        FirebaseStorage.getInstance("gs://post-3424f.firebasestorage.app") 
    }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // ════════════════════════════════════════════════════════════════════
    // TRANSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                ensureAuth()
                // 1. Write WAV to temp file (memory-safe)
                tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
                writeWavToFile(audioData, tempFile)

                // 2. Upload to Firebase Storage
                val filename = "${UUID.randomUUID()}.wav"
                val storageRef = storage.reference.child("uploads/$filename")
                
                Log.d(TAG, "Uploading ${tempFile.length()} bytes...")
                storageRef.putFile(Uri.fromFile(tempFile)).await()

                // 3. Call transcription function
                val gcsUri = "gs://${storageRef.bucket}/uploads/$filename"
                Log.d(TAG, "Transcribing...")
                
                val result = functions
                    .getHttpsCallable("transcribeChirp")
                    .apply { setTimeout(300, TimeUnit.SECONDS) }
                    .call(mapOf("gcsUri" to gcsUri))
                    .await()

                // 4. Cleanup remote file (async, non-blocking)
                storageRef.delete().addOnFailureListener { 
                    Log.w(TAG, "Remote cleanup failed", it) 
                }

                // 5. Parse result
                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val text = response?.get("text") as? String ?: ""
                
                Log.d(TAG, "Transcription done: ${text.take(50)}...")
                TranscriptionResult.Success(text)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error(e.message ?: "Transcription failed")
            } finally {
                // Always delete temp file
                tempFile?.delete()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AI POLISH
    // ════════════════════════════════════════════════════════════════════

    override suspend fun polish(text: String): String? {
        if (text.isBlank() || text.length <= 20) return text

        return withContext(Dispatchers.IO) {
            try {
                ensureAuth()
                
                Log.d(TAG, "Polishing ${text.length} chars...")
                
                val result = functions
                    .getHttpsCallable("cleanupText")
                    .apply { setTimeout(300, TimeUnit.SECONDS) }
                    .call(mapOf("text" to text))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val polished = response?.get("text") as? String ?: text
                
                Log.d(TAG, "Polish done: ${polished.take(50)}...")
                polished

            } catch (e: Exception) {
                Log.e(TAG, "Polish failed", e)
                null
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                Log.d(TAG, "Anonymous auth success")
            } catch (e: Exception) {
                Log.w(TAG, "Anonymous auth failed", e)
            }
        }
    }

    private fun writeWavToFile(pcmData: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use { stream ->
            stream.write(createWavHeader(pcmData.size))
            stream.write(pcmData)
        }
    }

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

actual fun createCloudTranscriptionService(): CloudTranscriptionService = 
    AndroidCloudTranscriptionService()
