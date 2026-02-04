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
 * **Cloud Transcription Service (Android Implementation)**
 * 
 * This class acts as the unified gateway for all server-side AI operations.
 * It abstracts away the complexity of file uploads, cloud function calls, and authentication.
 * 
 * **Key Responsibilities:**
 * 1. **Transcription:** Uploads audio -> Calls 'transcribeChirp' -> Returns text.
 * 2. **AI Polish:** Calls 'cleanupText' -> Returns polished text.
 * 
 * **Architecture Note:**
 * We intentionally keep this logic here rather than in ViewModels to ensure 
 * complete separation of concerns. The UI layer never touches raw audio bytes or Firebase APIs.
 */
class AndroidCloudTranscriptionService : CloudTranscriptionService, KoinComponent {

    private val context: Context by inject()

    // ─────────────────────────────────────────────────────────────────────────────
    // FIREBASE INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────────
    
    // Lazy initialization ensures we don't block app startup with Firebase SDK loading.
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    
    // Hardcoded bucket for stability.
    // In a production app, this might come from a remote config or resource file.
    private val storage: FirebaseStorage by lazy { 
        FirebaseStorage.getInstance("gs://post-3424f.firebasestorage.app") 
    }
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // ════════════════════════════════════════════════════════════════════════════
    // 1. TRANSCRIPTION PIPELINE
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Orchestrates the transcription process:
     * Audio Bytes -> WAV File -> Firebase Storage -> Cloud Function -> Text
     * 
     * @param audioData Raw PCM audio bytes (16kHz, 16-bit, Mono).
     * @return TranscriptionResult (Success with text or Error).
     */
    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // Step 0: Ensure we are authenticated (Anonymous auth is sufficient)
                ensureAuth()

                // Step 1: Calculate duration to help the server optimize execution
                // The server uses this to decide between Sync (fast) and Batch (robust) API calls.
                val durationSeconds = AudioConfig.calculateDurationSeconds(audioData)
                Log.d(TAG, "Audio duration: ${durationSeconds}s")

                // Step 2: Write raw bytes to a local WAV file
                // We use a temp file instead of uploading bytes directly to avoid OutOfMemoryErrors
                // on large recordings and to ensure a valid WAV header is present.
                tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
                writeWavToFile(audioData, tempFile)

                // Step 3: Upload to Firebase Storage
                // The Cloud Function cannot accept large payloads directly, so we upload via Storage.
                val filename = "${UUID.randomUUID()}.wav"
                val storageRef = storage.reference.child("uploads/$filename")

                Log.d(TAG, "Uploading ${tempFile.length()} bytes...")
                storageRef.putFile(Uri.fromFile(tempFile)).await()

                // Step 4: Trigger the Cloud Function
                // We pass the 'gcsUri' so the function knows where to pull the audio from.
                val gcsUri = "gs://${storageRef.bucket}/uploads/$filename"
                Log.d(TAG, "Transcribing (${if (durationSeconds <= 55) "sync" else "batch"} mode)...")

                val result = functions
                    .getHttpsCallable("transcribeChirp")
                    .apply { setTimeout(300, TimeUnit.SECONDS) } // 5 min timeout for long audio
                    .call(mapOf(
                        "gcsUri" to gcsUri,
                        "durationSeconds" to durationSeconds
                    ))
                    .await()

                // Note: The Cloud Function handles the cleanup of the uploaded file from Storage.
                // We only need to clean up our local temp file.

                // Step 5: Parse and Return Result
                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val text = response?.get("text") as? String ?: ""

                Log.d(TAG, "Transcription done: ${text.take(50)}...")
                TranscriptionResult.Success(text)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription pipeline failed", e)
                TranscriptionResult.Error(e.message ?: "Unknown error during transcription")
            } finally {
                // Critical: Always delete the local temp file to save user space
                tempFile?.delete()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. AI POLISHING PIPELINE
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Sends the transcribed text to Gemini to "polish" it.
     * Polishing includes removing filler words, fixing grammar, and structuring the text.
     * 
     * @param text Raw transcription text.
     * @return Polished text, or the original text if polishing failed/was skipped.
     */
    override suspend fun polish(text: String): String? {
        // Optimization: Don't polish very short or empty text
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
                null // Return null to indicate failure, caller allows manual retry or fallback
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. INTERNAL HELPERS
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Ensures the user is signed in to Firebase Auth.
     * We use Anonymous Auth to secure our Cloud Functions (requiring 'auth != null').
     */
    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                Log.d(TAG, "Anonymous auth success")
            } catch (e: Exception) {
                Log.w(TAG, "Anonymous auth failed", e)
                throw e
            }
        }
    }

    /**
     * Writes raw PCM bytes to a WAV file with a valid header.
     * Android MediaRecorder doesn't easily give us raw WAV for upload, so we construct it manually.
     */
    private fun writeWavToFile(pcmData: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use { stream ->
            stream.write(createWavHeader(pcmData.size))
            stream.write(pcmData)
        }
    }

    /**
     * Generates a standard 44-byte WAV (RIFF) header.
     * Essential for Google Cloud Speech-to-Text to recognize the file format correctly.
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

actual fun createCloudTranscriptionService(): CloudTranscriptionService = 
    AndroidCloudTranscriptionService()
