package com.liftley.vodrop.stt

import android.content.Context
import com.liftley.vodrop.audio.AudioConfig
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

/**
 * Android Speech-to-Text engine
 * NOTE: Placeholder for STT - actual Whisper.cpp JNI integration pending
 */
class AndroidSpeechToTextEngine : SpeechToTextEngine, KoinComponent {

    private val context: Context by inject()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    private val httpClient = HttpClient(OkHttp) {
        // Disable response body caching to prevent OOM
        expectSuccess = true
    }

    private val modelDirectory: File by lazy {
        File(context.filesDir, "whisper_models").apply { mkdirs() }
    }

    override suspend fun loadModel(model: WhisperModel) {
        _currentModel = model

        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelDirectory, model.fileName)

                // Download model if not present
                if (!modelFile.exists()) {
                    downloadModel(model, modelFile)
                }

                _modelState.value = ModelState.Loading

                // TODO: Load model with JNI when whisper.cpp Android integration is complete
                kotlinx.coroutines.delay(300)

                _modelState.value = ModelState.Ready

            } catch (e: Exception) {
                val error = "Failed to load model: ${e.message}"
                _modelState.value = ModelState.Error(error)
                throw SpeechToTextException(error, e)
            }
        }
    }

    /**
     * Download model with STREAMING to avoid OOM
     */
    private suspend fun downloadModel(model: WhisperModel, targetFile: File) {
        _modelState.value = ModelState.Downloading(0f)

        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

            // Use prepareGet for streaming download
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
                    output.flush()
                }
            }

            // Atomic rename
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model file")
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
        if (_modelState.value !is ModelState.Ready) {
            return TranscriptionResult.Error("Model not loaded")
        }

        return withContext(Dispatchers.Default) {
            try {
                val durationSeconds = AudioConfig.calculateDurationSeconds(audioData)

                // TODO: Replace with actual JNI call to whisper.cpp
                TranscriptionResult.Success(
                    text = "[Android: ${String.format("%.1f", durationSeconds)}s audio - JNI pending]",
                    durationMs = 0
                )
            } catch (e: Exception) {
                TranscriptionResult.Error("Transcription error: ${e.message}")
            }
        }
    }

    override fun release() {
        httpClient.close()
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()