package com.liftley.vodrop.stt

import android.content.Context
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

/**
 * Android Speech-to-Text engine using native Whisper.cpp.
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

    private fun getModelInfo(model: WhisperModel): ModelInfo = when (model) {
        WhisperModel.FAST -> ModelInfo(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            fileName = "ggml-tiny.en.bin",
            sizeBytes = 75_000_000L
        )
        WhisperModel.BALANCED -> ModelInfo(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
            fileName = "ggml-base.en.bin",
            sizeBytes = 142_000_000L
        )
        WhisperModel.QUALITY -> ModelInfo(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin",
            fileName = "ggml-small.en.bin",
            sizeBytes = 466_000_000L
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
                    }

                    _modelState.value = ModelState.Loading

                    // Release previous context if any
                    if (nativeContext != 0L) {
                        WhisperJni.release(nativeContext)
                        nativeContext = 0L
                    }

                    // Initialize native whisper
                    nativeContext = WhisperJni.init(modelFile.absolutePath)

                    if (nativeContext == 0L) {
                        throw SpeechToTextException("Failed to load native model")
                    }

                    _modelState.value = ModelState.Ready

                } catch (e: Exception) {
                    val errorMsg = "Failed to load model: ${e.message}"
                    _modelState.value = ModelState.Error(errorMsg)
                    throw SpeechToTextException(errorMsg, e)
                }
            }
        }
    }

    private suspend fun downloadModel(info: ModelInfo, targetFile: File) {
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

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model file")
            }

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    override fun isModelAvailable(model: WhisperModel): Boolean {
        val info = getModelInfo(model)
        return File(modelDirectory, info.fileName).exists()
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        if (nativeContext == 0L || _modelState.value !is ModelState.Ready) {
            return TranscriptionResult.Error("Model not loaded")
        }

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

                    // Call native transcription
                    val text = WhisperJni.transcribe(ctx, audioData)

                    val durationMs = System.currentTimeMillis() - startTime

                    if (text.isBlank()) {
                        TranscriptionResult.Success(
                            text = "(No speech detected)",
                            durationMs = durationMs
                        )
                    } else {
                        TranscriptionResult.Success(
                            text = text.trim(),
                            durationMs = durationMs
                        )
                    }
                } catch (e: Exception) {
                    TranscriptionResult.Error("Transcription error: ${e.message}")
                } finally {
                    isTranscribing = false
                }
            }
        }
    }

    override fun release() {
        // Only release if not transcribing
        if (!isTranscribing && nativeContext != 0L) {
            WhisperJni.release(nativeContext)
            nativeContext = 0L
        }
        httpClient.close()
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()