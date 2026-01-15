package com.liftley.vodrop.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Available Whisper models - 3 clear choices
 */
enum class WhisperModel(
    val fileName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val sizeDisplay: String,
    val sizeBytes: Long,
    val downloadUrl: String
) {
    FAST(
        fileName = "ggml-tiny.en.bin",
        displayName = "Fast",
        emoji = "⚡",
        description = "Quick results, good for short notes",
        sizeDisplay = "75 MB",
        sizeBytes = 75_000_000L,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
    ),
    BALANCED(
        fileName = "ggml-small.en.bin",
        displayName = "Balanced",
        emoji = "⚖️",
        description = "Great accuracy with good speed",
        sizeDisplay = "466 MB",
        sizeBytes = 466_000_000L,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin"
    ),
    QUALITY(
        fileName = "ggml-medium.en.bin",
        displayName = "Quality",
        emoji = "✨",
        description = "Best accuracy for important work",
        sizeDisplay = "1.5 GB",
        sizeBytes = 1_500_000_000L,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.en.bin"
    );

    companion object {
        val DEFAULT = BALANCED

        fun fromFileName(fileName: String): WhisperModel? {
            return entries.find { it.fileName == fileName }
        }
    }
}

sealed interface ModelState {
    data object NotLoaded : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class Error(val message: String) : ModelState
}

sealed interface TranscriptionResult {
    data class Success(val text: String, val durationMs: Long) : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

interface SpeechToTextEngine {
    val modelState: StateFlow<ModelState>
    val currentModel: WhisperModel

    suspend fun loadModel(model: WhisperModel = WhisperModel.DEFAULT)
    fun isModelAvailable(model: WhisperModel = WhisperModel.DEFAULT): Boolean
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    fun release()
}

class SpeechToTextException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

expect fun createSpeechToTextEngine(): SpeechToTextEngine