package com.liftley.vodrop.stt

import com.liftley.vodrop.audio.AudioConfig
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
import io.github.givimad.whisperjni.WhisperSamplingStrategy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * JVM/Desktop Speech-to-Text engine using WhisperJNI library
 * Wraps whisper.cpp with JNI for native performance
 */
class JvmSpeechToTextEngine : SpeechToTextEngine {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    private var whisper: WhisperJNI? = null
    private var whisperContext: WhisperContext? = null
    private val httpClient = HttpClient(OkHttp)

    private val modelDirectory: File by lazy {
        val userHome = System.getProperty("user.home")
        File(userHome, ".vodrop/models").apply { mkdirs() }
    }

    companion object {
        @Volatile
        private var libraryLoaded = false
        private val libraryLock = Any()

        private fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                synchronized(libraryLock) {
                    if (!libraryLoaded) {
                        try {
                            WhisperJNI.loadLibrary()
                            WhisperJNI.setLibraryLogger(null) // Disable native logging
                            libraryLoaded = true
                        } catch (e: Exception) {
                            throw SpeechToTextException("Failed to load WhisperJNI native library", e)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadModel(model: WhisperModel) {
        _currentModel = model

        withContext(Dispatchers.IO) {
            try {
                // Ensure native library is loaded
                ensureLibraryLoaded()

                val modelFile = File(modelDirectory, model.fileName)

                // Download model if not present
                if (!modelFile.exists()) {
                    downloadModel(model, modelFile)
                }

                // Load model into Whisper context
                _modelState.value = ModelState.Loading

                // Release previous context if any
                whisperContext?.close()

                whisper = WhisperJNI()
                whisperContext = whisper?.init(modelFile.toPath())
                    ?: throw SpeechToTextException("Failed to initialize Whisper context")

                _modelState.value = ModelState.Ready

            } catch (e: SpeechToTextException) {
                _modelState.value = ModelState.Error(e.message ?: "Unknown error")
                throw e
            } catch (e: Exception) {
                val error = "Failed to load model: ${e.message}"
                _modelState.value = ModelState.Error(error)
                throw SpeechToTextException(error, e)
            }
        }
    }

    /**
     * Download model from HuggingFace with progress tracking
     */
    // In the downloadModel function, replace:
    private suspend fun downloadModel(model: WhisperModel, targetFile: File) {
        _modelState.value = ModelState.Downloading(0f)

        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

            httpClient.prepareGet(model.downloadUrl).execute { response ->
                if (!response.status.isSuccess()) {
                    throw SpeechToTextException("Download failed: HTTP ${response.status.value}")
                }

                val contentLength = response.contentLength() ?: model.sizeBytes
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
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model")
            }

        } catch (e: SpeechToTextException) {
            throw e
        } catch (e: Exception) {
            throw SpeechToTextException("Download failed: ${e.message}", e)
        }
    }

    override fun isModelAvailable(model: WhisperModel): Boolean {
        return File(modelDirectory, model.fileName).exists()
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        val ctx = whisperContext
            ?: return TranscriptionResult.Error("Model not loaded. Please wait for initialization.")

        val whisperInstance = whisper
            ?: return TranscriptionResult.Error("Whisper not initialized")

        return withContext(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()

                // Convert PCM bytes to float samples
                val samples = AudioConfig.pcmBytesToFloatSamples(audioData)

                // Configure transcription parameters
                val params = WhisperFullParams(WhisperSamplingStrategy.GREEDY).apply {
                    nThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
                    printProgress = false
                    printTimestamps = false
                    printSpecial = false
                    translate = false
                    language = "en" // Auto-detect would be "auto"
                    suppressBlank = true
                    suppressNonSpeechTokens = true
                }

                // Run transcription
                val result = whisperInstance.full(ctx, params, samples, samples.size)

                if (result != 0) {
                    return@withContext TranscriptionResult.Error("Transcription failed with code: $result")
                }

                // Collect all segments
                val numSegments = whisperInstance.fullNSegments(ctx)
                val transcription = buildString {
                    for (i in 0 until numSegments) {
                        val segmentText = whisperInstance.fullGetSegmentText(ctx, i)
                        append(segmentText)
                    }
                }

                val durationMs = System.currentTimeMillis() - startTime

                TranscriptionResult.Success(
                    text = transcription.trim(),
                    durationMs = durationMs
                )

            } catch (e: Exception) {
                TranscriptionResult.Error("Transcription error: ${e.message}")
            }
        }
    }

    override fun release() {
        whisperContext?.close()
        whisperContext = null
        whisper = null
        httpClient.close()
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()