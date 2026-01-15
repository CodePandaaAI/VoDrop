package com.liftley.vodrop.stt

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Shared model download manager using Ktor for cross-platform HTTP
 */
class ModelManager(
    private val httpClient: HttpClient,
    private val getModelPath: (WhisperModel) -> String
) {
    /**
     * Download model from HuggingFace with progress tracking
     */
    fun downloadModel(model: WhisperModel): Flow<Float> = flow {
        emit(0f)

        withContext(Dispatchers.Default) {
            val response: HttpResponse = httpClient.get(model.downloadUrl) {
                headers {
                    append(HttpHeaders.Accept, "*/*")
                }
            }

            if (!response.status.isSuccess()) {
                throw SpeechToTextException("Failed to download model: ${response.status}")
            }

            val contentLength = response.contentLength() ?: model.sizeBytes
            val channel = response.bodyAsChannel()
            val modelPath = getModelPath(model)

            var downloaded = 0L
            val buffer = ByteArray(8192)

            // Platform-specific file writing will be handled by actual implementations
            writeModelFile(modelPath) { outputStream ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read > 0) {
                        outputStream(buffer, 0, read)
                        downloaded += read
                        emit(downloaded.toFloat() / contentLength.toFloat())
                    }
                }
            }
        }

        emit(1f)
    }
}

/**
 * Platform-specific file writing
 */
expect suspend fun writeModelFile(
    path: String,
    writeBlock: suspend ((ByteArray, Int, Int) -> Unit) -> Unit
)

/**
 * Platform-specific model directory
 */
expect fun getModelDirectory(): String