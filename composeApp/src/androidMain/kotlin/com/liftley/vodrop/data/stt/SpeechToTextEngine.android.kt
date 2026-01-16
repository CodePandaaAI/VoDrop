package com.liftley.vodrop.data.stt

import android.content.Context
import android.util.Log
import com.liftley.vodrop.data.audio.stt.WhisperJni
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
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
import okhttp3.ConnectionPool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "AndroidSTTEngine"

/**
 * Android Speech-to-Text engine using native Whisper.cpp.
 *
 * OPTIMIZED for battery life:
 * - Lazy HTTP client initialization
 * - Pre-compiled regex patterns
 * - Model unloading after inactivity
 * - Reduced connection pool
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

    // ⚡ OPTIMIZED: Track last usage for model unloading
    @Volatile
    private var lastUsageTime: Long = 0L

    @Volatile
    private var llmServiceInitialized = false

    @Volatile
    private var httpClientInitialized = false

    companion object {
        // Unload model after 5 minutes of inactivity to save memory
        private const val MODEL_UNLOAD_TIMEOUT_MS = 5 * 60 * 1000L

        // Minimum text length for LLM cleanup (saves network calls)
        private const val MIN_LLM_CLEANUP_LENGTH = 50

        // ⚡ OPTIMIZED: Pre-compiled regex patterns (compiled once, used many times)
        private val WHITESPACE_REGEX = Regex("\\s+")

        private val FILLER_PATTERNS = listOf(
            Regex("\\bum+\\b", RegexOption.IGNORE_CASE),
            Regex("\\buh+\\b", RegexOption.IGNORE_CASE),
            Regex("\\bah+\\b", RegexOption.IGNORE_CASE),
            Regex("\\beh+\\b", RegexOption.IGNORE_CASE),
            Regex("\\bmm+\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhm+\\b", RegexOption.IGNORE_CASE),
            Regex("\\ber+\\b", RegexOption.IGNORE_CASE),
            Regex("\\blike\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\b(you know)\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\b(i mean)\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\bso+\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\bwell\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\bbasically\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\bactually\\b(?=\\s*,)", RegexOption.IGNORE_CASE),
            Regex("\\bright\\b(?=\\s*[,?])", RegexOption.IGNORE_CASE),
            Regex("\\bokay so\\b", RegexOption.IGNORE_CASE),
            Regex("\\byeah so\\b", RegexOption.IGNORE_CASE)
        )

        private val REPEATED_WORD_REGEX = Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE)
        private val REPEATED_PHRASE_2_REGEX = Regex("\\b(\\w+\\s+\\w+),?\\s+\\1\\b", RegexOption.IGNORE_CASE)
        private val REPEATED_PHRASE_3_REGEX = Regex("\\b(\\w+\\s+\\w+\\s+\\w+),?\\s+\\1\\b", RegexOption.IGNORE_CASE)

        private val CORRECTIONS = listOf(
            Regex("\\bu\\b", RegexOption.IGNORE_CASE) to "you",
            Regex("\\bur\\b", RegexOption.IGNORE_CASE) to "your",
            Regex("\\br\\b", RegexOption.IGNORE_CASE) to "are",
            Regex("\\bcuz\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\bcause\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
            Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
            Regex("\\bgotta\\b", RegexOption.IGNORE_CASE) to "got to",
            Regex("\\bkinda\\b", RegexOption.IGNORE_CASE) to "kind of",
            Regex("\\bsorta\\b", RegexOption.IGNORE_CASE) to "sort of",
            Regex("\\bdunno\\b", RegexOption.IGNORE_CASE) to "don't know",
            Regex("\\blemme\\b", RegexOption.IGNORE_CASE) to "let me",
            Regex("\\bgimme\\b", RegexOption.IGNORE_CASE) to "give me"
        )

        private val MULTIPLE_COMMAS_REGEX = Regex(",\\s*,")
        private val COMMA_SPACING_REGEX = Regex("\\s*,\\s*")
        private val COMMA_AT_START_REGEX = Regex("^\\s*,\\s*")
        private val PERIOD_COMMA_REGEX = Regex("\\.\\s*,")
        private val SENTENCE_CAPITALIZE_REGEX = Regex("([.!?])\\s+([a-z])")
        private val STANDALONE_I_REGEX = Regex("\\bi\\b")
    }

    // ⚡ OPTIMIZED: Lazy HTTP client with reduced connection pool
    private val httpClient: HttpClient by lazy {
        httpClientInitialized = true
        HttpClient(OkHttp) {
            expectSuccess = true
            engine {
                config {
                    retryOnConnectionFailure(false)
                    // Reduce idle connections to save battery
                    connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
    }

    private val modelDirectory: File by lazy {
        File(context.filesDir, "whisper_models").apply { mkdirs() }
    }

    // ⚡ OPTIMIZED: Lazy LLM service (only created when first used)
    private val llmService: GeminiCleanupService by lazy {
        llmServiceInitialized = true
        GeminiCleanupService(LLMConfig.GEMINI_API_KEY)
    }

    private data class ModelInfo(
        val url: String,
        val fileName: String,
        val sizeBytes: Long
    )

    private fun getModelInfo(model: WhisperModel): ModelInfo = when (model) {
        WhisperModel.BALANCED -> ModelInfo(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            fileName = "ggml-base-q5_1.bin",
            sizeBytes = 57_000_000L
        )
        WhisperModel.QUALITY -> ModelInfo(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            fileName = "ggml-small-q5_1.bin",
            sizeBytes = 181_000_000L
        )
    }

    override suspend fun loadModel(model: WhisperModel) {
        if (isTranscribing) {
            throw SpeechToTextException("Cannot switch models while transcription is in progress")
        }

        _currentModel = model
        val info = getModelInfo(model)

        withContext(Dispatchers.IO) {
            contextMutex.withLock {
                try {
                    val modelFile = File(modelDirectory, info.fileName)

                    if (!modelFile.exists()) {
                        downloadModel(info, modelFile)
                    } else {
                        val fileSize = modelFile.length()
                        Log.d(LOG_TAG, "Model file exists, size: $fileSize bytes")
                        if (fileSize < info.sizeBytes * 0.9) {
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

                    Log.d(LOG_TAG, "Initializing native context from: ${modelFile.absolutePath}")
                    nativeContext = WhisperJni.init(modelFile.absolutePath)

                    if (nativeContext == 0L) {
                        throw SpeechToTextException("Failed to load native model - context is null")
                    }

                    Log.d(LOG_TAG, "Native context initialized successfully: $nativeContext")
                    lastUsageTime = System.currentTimeMillis()
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
            contextMutex.withLock {
                isTranscribing = true
                try {
                    val startTime = System.currentTimeMillis()
                    lastUsageTime = startTime

                    val ctx = nativeContext
                    if (ctx == 0L) {
                        return@withLock TranscriptionResult.Error("Model was unloaded")
                    }

                    // Convert PCM bytes to float samples
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
                        // Apply rule-based cleanup (fast, uses pre-compiled regex)
                        var cleanedText = cleanupTranscription(text)

                        // ⚡ OPTIMIZED: Only use LLM for texts > MIN_LLM_CLEANUP_LENGTH chars
                        // This saves network calls for short transcriptions
                        if (LLMConfig.isLLMCleanupEnabled &&
                            llmService.isAvailable() &&
                            cleanedText.length > MIN_LLM_CLEANUP_LENGTH) {

                            Log.d(LOG_TAG, "Attempting LLM cleanup for ${cleanedText.length} chars...")
                            try {
                                val llmResult = llmService.cleanupText(cleanedText)
                                if (llmResult.isSuccess) {
                                    cleanedText = llmResult.getOrDefault(cleanedText)
                                    Log.d(LOG_TAG, "LLM cleanup applied successfully")
                                } else {
                                    Log.w(LOG_TAG, "LLM cleanup failed, using rule-based result")
                                }
                            } catch (e: Exception) {
                                Log.w(LOG_TAG, "LLM cleanup error: ${e.message}")
                            }
                        }

                        val totalDurationMs = System.currentTimeMillis() - startTime

                        TranscriptionResult.Success(
                            text = cleanedText,
                            durationMs = totalDurationMs
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
     * ⚡ OPTIMIZED: Check if model should be unloaded due to inactivity.
     * Call this periodically or when app goes to background.
     */
    override fun checkAndUnloadIfInactive() {
        val currentTime = System.currentTimeMillis()
        val inactive = currentTime - lastUsageTime > MODEL_UNLOAD_TIMEOUT_MS

        if (inactive && nativeContext != 0L && !isTranscribing) {
            Log.d(LOG_TAG, "Unloading model due to inactivity")
            try {
                WhisperJni.release(nativeContext)
                nativeContext = 0L
                _modelState.value = ModelState.NotLoaded
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error unloading model", e)
            }
        }
    }

    /**
     * ⚡ OPTIMIZED: Uses pre-compiled regex patterns for faster cleanup
     */
    private fun cleanupTranscription(text: String): String {
        if (text.isBlank()) return text

        var result = text.trim()

        // Step 1: Normalize whitespace
        result = result.replace(WHITESPACE_REGEX, " ")

        // Step 2: Remove filler words (using pre-compiled patterns)
        for (pattern in FILLER_PATTERNS) {
            result = result.replace(pattern, "")
        }

        // Step 3: Remove repeated words
        result = result.replace(REPEATED_WORD_REGEX) { match ->
            match.groupValues[1]
        }

        // Step 4: Remove repeated phrases
        result = result.replace(REPEATED_PHRASE_2_REGEX) { match ->
            match.groupValues[1]
        }
        result = result.replace(REPEATED_PHRASE_3_REGEX) { match ->
            match.groupValues[1]
        }

        // Step 5: Fix common speech-to-text errors
        for ((pattern, replacement) in CORRECTIONS) {
            result = result.replace(pattern, replacement)
        }

        // Step 6: Clean up punctuation
        result = result.replace(MULTIPLE_COMMAS_REGEX, ",")
        result = result.replace(COMMA_SPACING_REGEX, ", ")
        result = result.replace(WHITESPACE_REGEX, " ")
        result = result.replace(COMMA_AT_START_REGEX, "")
        result = result.replace(PERIOD_COMMA_REGEX, ".")

        // Step 7: Capitalize properly
        result = result.trim()
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { it.uppercaseChar() }
        }
        result = result.replace(SENTENCE_CAPITALIZE_REGEX) { match ->
            "${match.groupValues[1]} ${match.groupValues[2].uppercase()}"
        }
        result = result.replace(STANDALONE_I_REGEX, "I")

        // Step 8: Add ending punctuation if missing
        result = result.trim()
        if (result.isNotEmpty() && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result = "$result."
        }

        // Final cleanup
        result = result.replace(WHITESPACE_REGEX, " ").trim()

        return result
    }

    override fun release() {
        Log.d(LOG_TAG, "Releasing engine resources")
        if (!isTranscribing && nativeContext != 0L) {
            try {
                WhisperJni.release(nativeContext)
                Log.d(LOG_TAG, "Native context released")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error releasing native context", e)
            }
            nativeContext = 0L
        }

        // Close LLM service
        if (llmServiceInitialized) {
            try {
                llmService.close()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Error closing LLM service", e)
            }
        }

        // ⚡ Close HTTP client to release connections
        if (httpClientInitialized) {
            try {
                httpClient.close()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Error closing HTTP client", e)
            }
        }

        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()