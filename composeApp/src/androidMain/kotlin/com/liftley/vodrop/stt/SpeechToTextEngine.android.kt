package com.liftley.vodrop.stt

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

private const val LOG_TAG = "AndroidSTTEngine"

/**
 * Android Speech-to-Text engine using native Whisper.cpp.
 *
 * Uses quantized multilingual models for:
 * - Better accuracy with accents
 * - Faster inference
 * - Smaller download sizes
 */
class AndroidSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val context: Context by inject()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    // Native context pointer (0 = not loaded)
    private var nativeContext: Long = 0L

    // Mutex to prevent concurrent access to native context
    private val contextMutex = Mutex()

    // Flag to track if transcription is in progress
    @Volatile
    private var isTranscribing = false

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    private val modelDirectory: File by lazy {
        File(context.filesDir, "whisper_models").apply { mkdirs() }
    }

    private data class ModelInfo(
        val url: String,
        val fileName: String,
        val sizeBytes: Long
    )

    /**
     * Get model info - using QUANTIZED MULTILINGUAL models for:
     * - Better accent support
     * - Faster inference (2-3x faster than fp16)
     * - Smaller downloads
     */
    private fun getModelInfo(model: WhisperModel): ModelInfo = when (model) {
        WhisperModel.FAST -> ModelInfo(
            // Tiny English - smallest and fastest
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            fileName = "ggml-tiny.en.bin",
            sizeBytes = 75_000_000L
        )
        WhisperModel.BALANCED -> ModelInfo(
            // Base MULTILINGUAL quantized - better with accents
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            fileName = "ggml-base-q5_1.bin",
            sizeBytes = 57_000_000L
        )
        WhisperModel.QUALITY -> ModelInfo(
            // Small MULTILINGUAL quantized - best quality for mobile
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            fileName = "ggml-small-q5_1.bin",
            sizeBytes = 181_000_000L
        )
    }

    override suspend fun loadModel(model: WhisperModel) {
        // Don't allow model switch while transcribing
        if (isTranscribing) {
            throw SpeechToTextException("Cannot switch models while transcription is in progress")
        }

        _currentModel = model
        val info = getModelInfo(model)

        withContext(Dispatchers.IO) {
            // Lock to prevent concurrent access
            contextMutex.withLock {
                try {
                    val modelFile = File(modelDirectory, info.fileName)

                    if (!modelFile.exists()) {
                        downloadModel(info, modelFile)
                    } else {
                        // Verify file size
                        val fileSize = modelFile.length()
                        Log.d(LOG_TAG, "Model file exists, size: $fileSize bytes")
                        if (fileSize < info.sizeBytes * 0.9) {
                            // File seems corrupted/incomplete, re-download
                            Log.w(LOG_TAG, "Model file seems incomplete, re-downloading...")
                            modelFile.delete()
                            downloadModel(info, modelFile)
                        }
                    }

                    _modelState.value = ModelState.Loading

                    // Release previous context if any
                    if (nativeContext != 0L) {
                        Log.d(LOG_TAG, "Releasing previous native context")
                        WhisperJni.release(nativeContext)
                        nativeContext = 0L
                    }

                    // Log system info before loading
                    try {
                        val sysInfo = WhisperJni.getSystemInfo()
                        Log.d(LOG_TAG, "Whisper System Info: $sysInfo")
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Failed to get system info: ${e.message}")
                    }

                    // Initialize native whisper
                    Log.d(LOG_TAG, "Initializing native context from: ${modelFile.absolutePath}")
                    nativeContext = WhisperJni.init(modelFile.absolutePath)

                    if (nativeContext == 0L) {
                        throw SpeechToTextException("Failed to load native model - context is null")
                    }

                    Log.d(LOG_TAG, "Native context initialized successfully: $nativeContext")
                    _modelState.value = ModelState.Ready

                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to load model", e)
                    val errorMsg = "Failed to load model: ${e.message}"
                    _modelState.value = ModelState.Error(errorMsg)
                    throw SpeechToTextException(errorMsg, e)
                }
            }
        }
    }

    private suspend fun downloadModel(info: ModelInfo, targetFile: File) {
        Log.d(LOG_TAG, "Starting model download: ${info.url}")
        _modelState.value = ModelState.Downloading(0f)

        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        try {
            httpClient.prepareGet(info.url).execute { response ->
                if (!response.status.isSuccess()) {
                    throw SpeechToTextException("Download failed: HTTP ${response.status.value}")
                }

                val contentLength = response.contentLength() ?: info.sizeBytes
                val channel: ByteReadChannel = response.bodyAsChannel()

                var downloaded = 0L
                val buffer = ByteArray(8192)

                FileOutputStream(tempFile).use { output ->
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val progress = (downloaded.toFloat() / contentLength).coerceIn(0f, 1f)
                            _modelState.value = ModelState.Downloading(progress)
                        }
                    }
                    output.flush()
                }
            }

            Log.d(LOG_TAG, "Download complete, moving temp file to target")
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model file")
            }

            Log.d(LOG_TAG, "Model saved successfully: ${targetFile.absolutePath}, size: ${targetFile.length()}")

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Download failed", e)
            tempFile.delete()
            throw e
        }
    }

    override fun isModelAvailable(model: WhisperModel): Boolean {
        val info = getModelInfo(model)
        val file = File(modelDirectory, info.fileName)
        return file.exists() && file.length() > info.sizeBytes * 0.9
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (nativeContext == 0L || _modelState.value !is ModelState.Ready) {
            Log.e(LOG_TAG, "Cannot transcribe: model not loaded (context=$nativeContext, state=${_modelState.value})")
            return TranscriptionResult.Error("Model not loaded")
        }

        if (audioData.isEmpty()) {
            Log.e(LOG_TAG, "Cannot transcribe: empty audio data")
            return TranscriptionResult.Error("No audio data provided")
        }

        Log.d(LOG_TAG, "Starting transcription of ${audioData.size} bytes")

        return withContext(Dispatchers.Default) {
            // Lock to prevent model switching during transcription
            contextMutex.withLock {
                isTranscribing = true
                try {
                    val startTime = System.currentTimeMillis()

                    // Check context again after acquiring lock
                    val ctx = nativeContext
                    if (ctx == 0L) {
                        return@withLock TranscriptionResult.Error("Model was unloaded")
                    }

                    // Convert PCM bytes to float samples
                    // Audio format: 16-bit signed PCM, little-endian, 16kHz, mono
                    val numSamples = audioData.size / 2
                    val samples = FloatArray(numSamples)

                    for (i in 0 until numSamples) {
                        val low = audioData[2 * i].toInt() and 0xFF
                        val high = audioData[2 * i + 1].toInt()
                        val sample = (high shl 8) or low
                        samples[i] = sample.toShort() / 32768.0f
                    }

                    val audioDuration = numSamples / 16000.0f
                    Log.d(LOG_TAG, "Converted to $numSamples float samples (${audioDuration}s audio)")

                    // Validate samples - check if audio isn't silent
                    val maxSample = samples.maxOrNull() ?: 0f
                    val minSample = samples.minOrNull() ?: 0f
                    Log.d(LOG_TAG, "Sample range: [$minSample, $maxSample]")

                    if (maxSample < 0.01f && minSample > -0.01f) {
                        Log.w(LOG_TAG, "Audio appears to be silent or very quiet")
                        return@withLock TranscriptionResult.Success(
                            text = "(No speech detected - audio too quiet)",
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }

                    // Call native transcription with float array
                    Log.d(LOG_TAG, "Calling native transcribe with context=$ctx")
                    val text = WhisperJni.transcribe(ctx, samples)

                    val durationMs = System.currentTimeMillis() - startTime
                    Log.d(LOG_TAG, "Transcription completed in ${durationMs}ms: '$text'")

                    if (text.isBlank()) {
                        TranscriptionResult.Success(
                            text = "(No speech detected)",
                            durationMs = durationMs
                        )
                    } else {
                        TranscriptionResult.Success(
                            text = formatTranscription(text),  // <-- Apply formatting here
                            durationMs = durationMs
                        )
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Transcription error", e)
                    TranscriptionResult.Error("Transcription error: ${e.message}")
                } finally {
                    isTranscribing = false
                }
            }
        }
    }
    /**
     * Simple text formatting - just cleans up without changing meaning.
     * - Trims whitespace
     * - Capitalizes first letter of sentences
     * - Ensures ending punctuation
     */
    private fun formatTranscription(text: String): String {
        if (text.isBlank()) return text

        var result = text.trim()

        // Remove extra spaces
        result = result.replace(Regex("\\s+"), " ")

        // Capitalize first letter
        result = result.replaceFirstChar { it.uppercaseChar() }

        // Capitalize after . ! ?
        result = result.replace(Regex("([.!?])\\s+([a-z])")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2].uppercase()}"
        }

        // Add period at end if no punctuation
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result = "$result."
        }

        return result
    }

    override fun release() {
        Log.d(LOG_TAG, "Releasing engine resources")
        // Only release if not transcribing
        if (!isTranscribing && nativeContext != 0L) {
            try {
                WhisperJni.release(nativeContext)
                Log.d(LOG_TAG, "Native context released")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error releasing native context", e)
            }
            nativeContext = 0L
        }
        httpClient.close()
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()