package com.liftley.vodrop.data.stt

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.File
import java.io.FileOutputStream

private const val LOG_TAG = "WhisperModelDownloader"

/**
 * Downloads Whisper models from HuggingFace.
 */
class WhisperModelDownloader(
    private val httpClient: HttpClient,
    private val modelDirectory: File
) {

    data class ModelInfo(
        val url: String,
        val fileName: String,
        val sizeBytes: Long
    )

    fun getModelInfo(model: WhisperModel): ModelInfo = when (model) {
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

    fun isModelAvailable(model: WhisperModel): Boolean {
        val info = getModelInfo(model)
        val file = File(modelDirectory, info.fileName)
        return file.exists() && file.length() > info.sizeBytes * 0.9
    }

    suspend fun downloadIfNeeded(
        model: WhisperModel,
        onProgress: (ModelState) -> Unit
    ): File {
        val info = getModelInfo(model)
        val modelFile = File(modelDirectory, info.fileName)

        if (modelFile.exists()) {
            val fileSize = modelFile.length()
            Log.d(LOG_TAG, "Model file exists, size: $fileSize bytes")

            if (fileSize >= info.sizeBytes * 0.9) {
                return modelFile
            }

            Log.w(LOG_TAG, "Model file incomplete, re-downloading...")
            modelFile.delete()
        }

        return download(info, modelFile, onProgress)
    }

    private suspend fun download(
        info: ModelInfo,
        targetFile: File,
        onProgress: (ModelState) -> Unit
    ): File {
        Log.d(LOG_TAG, "Starting download: ${info.url}")
        onProgress(ModelState.Downloading(0f))

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
                            onProgress(ModelState.Downloading(progress))
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw SpeechToTextException("Failed to save model file")
            }

            Log.d(LOG_TAG, "Model saved: ${targetFile.absolutePath}")
            return targetFile

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Download failed", e)
            tempFile.delete()
            throw e
        }
    }
}