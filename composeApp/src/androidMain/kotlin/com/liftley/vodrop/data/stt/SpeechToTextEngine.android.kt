package com.liftley.vodrop.data.stt

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val LOG_TAG = "CloudSTTEngine"

/**
 * Android Speech-to-Text engine using Groq Cloud API.
 *
 * Benefits:
 * - No native code (JNI) complexity
 * - No model downloads (saves 50-200MB)
 * - No battery drain from on-device inference
 * - Best accuracy (Whisper Large v3)
 * - Works on any Android device
 */
class CloudSpeechToTextEngine : SpeechToTextEngine {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.NotReady)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000  // 2 minutes for longer audio
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
    }

    private val groqService: GroqWhisperService by lazy {
        GroqWhisperService(
            apiKey = GroqConfig.API_KEY,
            httpClient = httpClient
        )
    }

    override suspend fun initialize() {
        // Cloud engine is always ready - no model to load!
        Log.d(LOG_TAG, "Cloud engine initialized (instant)")
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
                Log.d(LOG_TAG, "Sending ${audioData.size} bytes to Groq API...")

                val result = groqService.transcribe(audioData)

                _state.value = TranscriptionState.Ready

                when (result) {
                    is TranscriptionResult.Success -> {
                        Log.d(LOG_TAG, "Transcription complete: ${result.text.take(50)}...")
                        // Apply rule-based cleanup for basic polish
                        val cleanedText = RuleBasedTextCleanup.cleanup(result.text)
                        TranscriptionResult.Success(cleanedText, result.durationMs)
                    }
                    is TranscriptionResult.Error -> {
                        Log.e(LOG_TAG, "Transcription error: ${result.message}")
                        result
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Transcription exception", e)
                _state.value = TranscriptionState.Error(e.message ?: "Unknown error")
                TranscriptionResult.Error("Transcription failed: ${e.message}")
            }
        }
    }

    override fun release() {
        Log.d(LOG_TAG, "Releasing cloud engine resources")
        try {
            httpClient.close()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error closing HTTP client", e)
        }
        _state.value = TranscriptionState.NotReady
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = CloudSpeechToTextEngine()