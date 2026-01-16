package com.liftley.vodrop.data.stt

import android.content.Context
import android.util.Log
import com.liftley.vodrop.data.audio.stt.WhisperJni
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
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
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "AndroidSTTEngine"
private const val MODEL_UNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
private const val MIN_LLM_CLEANUP_LENGTH = 50

/**
 * Android Speech-to-Text engine using native Whisper.cpp.
 */
class AndroidSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val context: Context by inject()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    private var nativeContext: Long = 0L
    private val contextMutex = Mutex()

    @Volatile private var isTranscribing = false
    @Volatile private var lastUsageTime: Long = 0L
    @Volatile private var llmServiceInitialized = false
    @Volatile private var httpClientInitialized = false

    private val httpClient: HttpClient by lazy {
        httpClientInitialized = true
        HttpClient(OkHttp) {
            expectSuccess = true
            engine {
                config {
                    retryOnConnectionFailure(false)
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

    private val modelDownloader: WhisperModelDownloader by lazy {
        WhisperModelDownloader(httpClient, modelDirectory)
    }

    private val llmService: GeminiCleanupService by lazy {
        llmServiceInitialized = true
        GeminiCleanupService(LLMConfig.GEMINI_API_KEY)
    }

    override suspend fun loadModel(model: WhisperModel) {
        if (isTranscribing) {
            throw SpeechToTextException("Cannot switch models while transcription is in progress")
        }

        _currentModel = model

        withContext(Dispatchers.IO) {
            contextMutex.withLock {
                try {
                    val modelFile = modelDownloader.downloadIfNeeded(model) { state ->
                        _modelState.value = state
                    }

                    _modelState.value = ModelState.Loading

                    if (nativeContext != 0L) {
                        Log.d(LOG_TAG, "Releasing previous native context")
                        WhisperJni.release(nativeContext)
                        nativeContext = 0L
                    }

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

                    Log.d(LOG_TAG, "Native context initialized: $nativeContext")
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

    override fun isModelAvailable(model: WhisperModel): Boolean {
        return modelDownloader.isModelAvailable(model)
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (nativeContext == 0L || _modelState.value !is ModelState.Ready) {
            return TranscriptionResult.Error("Model not loaded")
        }

        if (audioData.isEmpty()) {
            return TranscriptionResult.Error("No audio data provided")
        }

        Log.d(LOG_TAG, "Transcribing ${audioData.size} bytes")

        return withContext(Dispatchers.Default) {
            contextMutex.withLock {
                isTranscribing = true
                try {
                    val startTime = System.currentTimeMillis()
                    lastUsageTime = startTime

                    val ctx = nativeContext
                    if (ctx == 0L) return@withLock TranscriptionResult.Error("Model was unloaded")

                    // Convert PCM to float samples
                    val samples = convertPcmToFloat(audioData)

                    if (isSilent(samples)) {
                        return@withLock TranscriptionResult.Success(
                            text = "(No speech detected - audio too quiet)",
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }

                    val text = WhisperJni.transcribe(ctx, samples)
                    val durationMs = System.currentTimeMillis() - startTime
                    Log.d(LOG_TAG, "Transcription in ${durationMs}ms: '$text'")

                    if (text.isBlank()) {
                        TranscriptionResult.Success("(No speech detected)", durationMs)
                    } else {
                        val cleanedText = processText(text)
                        TranscriptionResult.Success(cleanedText, System.currentTimeMillis() - startTime)
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

    private fun convertPcmToFloat(audioData: ByteArray): FloatArray {
        val numSamples = audioData.size / 2
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = audioData[2 * i].toInt() and 0xFF
            val high = audioData[2 * i + 1].toInt()
            val sample = (high shl 8) or low
            samples[i] = sample.toShort() / 32768.0f
        }
        return samples
    }

    private fun isSilent(samples: FloatArray): Boolean {
        val max = samples.maxOrNull() ?: 0f
        val min = samples.minOrNull() ?: 0f
        return max < 0.01f && min > -0.01f
    }

    private suspend fun processText(rawText: String): String {
        // Step 1: Rule-based cleanup (fast, local)
        var text = RuleBasedTextCleanup.cleanup(rawText)

        // Step 2: Optional LLM cleanup (if enabled and text is long enough)
        if (LLMConfig.isLLMCleanupEnabled &&
            llmService.isAvailable() &&
            text.length > MIN_LLM_CLEANUP_LENGTH) {

            try {
                val result = llmService.cleanupText(text)
                if (result.isSuccess) {
                    text = result.getOrDefault(text)
                    Log.d(LOG_TAG, "LLM cleanup applied")
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "LLM cleanup error: ${e.message}")
            }
        }

        return text
    }

    override fun checkAndUnloadIfInactive() {
        val inactive = System.currentTimeMillis() - lastUsageTime > MODEL_UNLOAD_TIMEOUT_MS
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

    override fun release() {
        Log.d(LOG_TAG, "Releasing engine resources")
        if (!isTranscribing && nativeContext != 0L) {
            try {
                WhisperJni.release(nativeContext)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error releasing native context", e)
            }
            nativeContext = 0L
        }

        if (llmServiceInitialized) {
            try { llmService.close() } catch (_: Exception) {}
        }

        if (httpClientInitialized) {
            try { httpClient.close() } catch (_: Exception) {}
        }

        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()