package com.liftley.vodrop.stt

import com.liftley.vodrop.audio.AudioConfig
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
 * Desktop (JVM) Speech-to-Text engine using WhisperJNI
 */
class JvmSpeechToTextEngine : SpeechToTextEngine {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    private var whisperContext: WhisperContext? = null
    private val whisperJNI = WhisperJNI()

    private val httpClient = HttpClient(OkHttp)

    private val modelDirectory: File by lazy {
        val userHome = System.getProperty("user.home")
        File(userHome, ".vodrop/models").apply { mkdirs() }
    }

    init {
        WhisperJNI.loadLibrary()
    }

    override suspend fun loadModel(model: WhisperModel) {
        _currentModel = model

        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelDirectory, model.fileName)

                // ✅ FIX #10: Clean up any leftover temp files
                modelDirectory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach {
                    it.delete()
                }

                if (!modelFile.exists()) {
                    downloadModel(model, modelFile)
                }

                _modelState.value = ModelState.Loading

                whisperContext?.close()
                whisperContext = whisperJNI.init(modelFile.toPath())

                if (whisperContext == null) {
                    throw SpeechToTextException("Failed to initialize Whisper context")
                }

                _modelState.value = ModelState.Ready

            } catch (e: Exception) {
                val error = "Failed to load model: ${e.message}"
                _modelState.value = ModelState.Error(error)
                throw SpeechToTextException(error, e)
            }
        }
    }

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
                    output.flush()
                }
            }

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
        val file = File(modelDirectory, model.fileName)
        return file.exists() && file.length() > model.sizeBytes * 0.9
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        val context = whisperContext
        if (context == null || _modelState.value !is ModelState.Ready) {
            return TranscriptionResult.Error("Model not loaded")
        }

        return withContext(Dispatchers.Default) {
            try {
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
                    return@withContext TranscriptionResult.Error("Transcription failed with code: $result")
                }

                val numSegments = whisperJNI.fullNSegments(context)
                val text = StringBuilder()
                for (i in 0 until numSegments) {
                    text.append(whisperJNI.fullGetSegmentText(context, i))
                }

                val durationMs = System.currentTimeMillis() - startTime

                TranscriptionResult.Success(
                    text = text.toString().trim(),
                    durationMs = durationMs
                )
            } catch (e: Exception) {
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

    // ✅ FIX #1: Add override for checkAndUnloadIfInactive
    override fun checkAndUnloadIfInactive() {
        // Desktop has plenty of RAM, no need to unload
        // This is intentionally empty
    }

    override fun release() {
        whisperContext?.close()
        whisperContext = null
        httpClient.close()
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()