package com.liftley.vodrop.data.stt

import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Model configuration for Desktop (offline)
 */
private object DesktopModelConfig {
    const val MODEL_NAME = "ggml-base-q5_1.bin"
    const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
    const val MODEL_SIZE = 57_000_000L
}

/**
 * Desktop (JVM) Speech-to-Text engine using WhisperJNI
 * Keeps offline capability for Desktop where it works great
 */
class JvmSpeechToTextEngine : SpeechToTextEngine {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.NotReady)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var whisperContext: WhisperContext? = null
    private val whisperJNI = WhisperJNI()

    private val httpClient = HttpClient(OkHttp)

    private val modelDirectory: File by lazy {
        val userHome = System.getProperty("user.home")
        File(userHome, ".vodrop/models").apply { mkdirs() }
    }

    private val modelFile: File
        get() = File(modelDirectory, DesktopModelConfig.MODEL_NAME)

    init {
        WhisperJNI.loadLibrary()
    }

    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                // Clean up temp files
                modelDirectory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach {
                    it.delete()
                }

                // Download model if needed
                if (!modelFile.exists() || modelFile.length() < DesktopModelConfig.MODEL_SIZE * 0.9) {
                    downloadModel()
                }

                // Load model
                _state.value = TranscriptionState.Initializing("Loading model...")

                whisperContext?.close()
                whisperContext = whisperJNI.init(modelFile.toPath())

                if (whisperContext == null) {
                    throw SpeechToTextException("Failed to initialize Whisper context")
                }

                _state.value = TranscriptionState.Ready
                println("✅ Desktop Whisper model loaded")

            } catch (e: Exception) {
                val error = "Failed to initialize: ${e.message}"
                _state.value = TranscriptionState.Error(error)
                throw SpeechToTextException(error, e)
            }
        }
    }

    private suspend fun downloadModel() {
        _state.value = TranscriptionState.Downloading(0f)

        try {
            modelFile.parentFile?.mkdirs()
            val tempFile = File(modelFile.parent, "${modelFile.name}.tmp")

            httpClient.prepareGet(DesktopModelConfig.MODEL_URL).execute { response ->
                if (!response.status.isSuccess()) {
                    throw SpeechToTextException("Download failed: HTTP ${response.status.value}")
                }

                val contentLength = response.contentLength() ?: DesktopModelConfig.MODEL_SIZE
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
                            _state.value = TranscriptionState.Downloading(progress)
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.renameTo(modelFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model file")
            }

        } catch (e: SpeechToTextException) {
            throw e
        } catch (e: Exception) {
            throw SpeechToTextException("Download failed: ${e.message}", e)
        }
    }

    override fun isReady(): Boolean = _state.value is TranscriptionState.Ready

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        val context = whisperContext
        if (context == null || !isReady()) {
            return TranscriptionResult.Error("Model not loaded")
        }

        return withContext(Dispatchers.Default) {
            try {
                _state.value = TranscriptionState.Transcribing
                val startTime = System.currentTimeMillis()

                val samples = convertBytesToFloatSamples(audioData)

                val params = WhisperFullParams().apply {
                    language = "en"
                    translate = false
                    noContext = true
                    singleSegment = false
                    printProgress = false
                    printTimestamps = false
                }

                val result = whisperJNI.full(context, params, samples, samples.size)
                if (result != 0) {
                    _state.value = TranscriptionState.Ready
                    return@withContext TranscriptionResult.Error("Transcription failed with code: $result")
                }

                val numSegments = whisperJNI.fullNSegments(context)
                val text = StringBuilder()
                for (i in 0 until numSegments) {
                    text.append(whisperJNI.fullGetSegmentText(context, i))
                }

                val durationMs = System.currentTimeMillis() - startTime
                _state.value = TranscriptionState.Ready

                // Apply basic cleanup
                val cleanedText = RuleBasedTextCleanup.cleanup(text.toString().trim())

                TranscriptionResult.Success(
                    text = cleanedText,
                    durationMs = durationMs
                )
            } catch (e: Exception) {
                _state.value = TranscriptionState.Ready
                TranscriptionResult.Error("Transcription error: ${e.message}")
            }
        }
    }

    private fun convertBytesToFloatSamples(audioData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(audioData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val samples = FloatArray(shortBuffer.remaining())
        for (i in samples.indices) {
            samples[i] = shortBuffer.get() / 32768.0f
        }
        return samples
    }

    override fun release() {
        whisperContext?.close()
        whisperContext = null
        httpClient.close()
        _state.value = TranscriptionState.NotReady
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()