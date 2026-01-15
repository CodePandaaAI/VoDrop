package com.liftley.vodrop.stt

import android.content.Context
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.WhisperKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.coroutines.resume

/**
 * Android Speech-to-Text engine using WhisperKitAndroid
 * Uses on-device Whisper models optimized for Qualcomm processors
 */
@OptIn(ExperimentalWhisperKit::class)
class AndroidSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val context: Context by inject()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    private var whisperKit: WhisperKit? = null
    private var isInitialized = false
    private var lastTranscriptionResult: String = ""

    private val modelDirectory: File by lazy {
        File(context.filesDir, "whisperkit_models").apply { mkdirs() }
    }

    override suspend fun loadModel(model: WhisperModel) {
        _currentModel = model

        withContext(Dispatchers.IO) {
            try {
                _modelState.value = ModelState.Downloading(0f)

                // Clean up previous instance
                whisperKit?.deinitialize()
                whisperKit = null
                isInitialized = false

                // Build WhisperKit with the selected model
                val kit = WhisperKit.Builder()
                    .setModel(getWhisperKitModelConstant(model))
                    .setApplicationContext(context.applicationContext)
                    .setCallback { what, result ->
                        handleCallback(what, result)
                    }
                    .build()

                whisperKit = kit

                // Load the model (downloads if needed)
                kit.loadModel().collect { progress ->
                    _modelState.value = ModelState.Downloading(progress.coerceIn(0f, 1f))
                }

                _modelState.value = ModelState.Loading

                // Initialize with audio parameters (16kHz, mono)
                kit.init(
                    frequency = 16000,
                    channels = 1,
                    duration = 0  // No limit
                )

                isInitialized = true
                _modelState.value = ModelState.Ready

            } catch (e: Exception) {
                val error = "Failed to load model: ${e.message}"
                _modelState.value = ModelState.Error(error)
                throw SpeechToTextException(error, e)
            }
        }
    }

    private fun getWhisperKitModelConstant(model: WhisperModel): String {
        return when (model) {
            WhisperModel.FAST -> WhisperKit.OPENAI_TINY_EN
            WhisperModel.BALANCED -> WhisperKit.OPENAI_BASE_EN
            WhisperModel.QUALITY -> WhisperKit.OPENAI_SMALL_EN
        }
    }

    private fun handleCallback(what: Int, result: WhisperKit.TextOutputCallback.Result) {
        when (what) {
            WhisperKit.TextOutputCallback.MSG_INIT -> {
                // Model initialized
            }
            WhisperKit.TextOutputCallback.MSG_TEXT_OUT -> {
                // Store the transcription result
                lastTranscriptionResult = result.text
            }
            WhisperKit.TextOutputCallback.MSG_CLOSE -> {
                // Cleanup
            }
        }
    }

    override fun isModelAvailable(model: WhisperModel): Boolean {
        // WhisperKit handles model caching internally
        // We check if we've previously initialized with this model
        return whisperKit != null && _currentModel == model && isInitialized
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        val kit = whisperKit

        if (kit == null || !isInitialized || _modelState.value !is ModelState.Ready) {
            return TranscriptionResult.Error("Model not loaded")
        }

        return withContext(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()

                // Reset the result
                lastTranscriptionResult = ""

                // Transcribe the audio data
                kit.transcribe(audioData)

                // Wait a moment for callback to be processed
                // WhisperKit uses callbacks so we need to wait
                val result = waitForTranscription(maxWaitMs = 30000)

                val durationMs = System.currentTimeMillis() - startTime

                if (result.isBlank()) {
                    TranscriptionResult.Success(
                        text = "(No speech detected)",
                        durationMs = durationMs
                    )
                } else {
                    TranscriptionResult.Success(
                        text = result.trim(),
                        durationMs = durationMs
                    )
                }
            } catch (e: Exception) {
                TranscriptionResult.Error("Transcription error: ${e.message}")
            }
        }
    }

    private suspend fun waitForTranscription(maxWaitMs: Long): String {
        return suspendCancellableCoroutine { continuation ->
            var waited = 0L
            val checkInterval = 100L

            Thread {
                while (waited < maxWaitMs) {
                    if (lastTranscriptionResult.isNotBlank()) {
                        continuation.resume(lastTranscriptionResult)
                        return@Thread
                    }
                    Thread.sleep(checkInterval)
                    waited += checkInterval
                }
                continuation.resume(lastTranscriptionResult)
            }.start()
        }
    }

    override fun release() {
        whisperKit?.deinitialize()
        whisperKit = null
        isInitialized = false
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()