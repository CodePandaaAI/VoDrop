package com.liftley.vodrop.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Available Whisper models - mapped to platform-specific implementations
 * Desktop: Whisper.cpp GGML models
 * Android: WhisperKit models (Qualcomm optimized)
 */
enum class WhisperModel(
    val displayName: String,
    val emoji: String,
    val description: String,
    val sizeDisplay: String,
    // Desktop-specific
    val ggmlFileName: String,
    val ggmlDownloadUrl: String,
    val ggmlSizeBytes: Long,
    // Android-specific (WhisperKit model ID)
    val whisperKitModelId: String
) {
    FAST(
        displayName = "Fast",
        emoji = "⚡",
        description = "Quick results, good for short notes",
        sizeDisplay = "~75 MB",
        ggmlFileName = "ggml-tiny.en.bin",
        ggmlDownloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
        ggmlSizeBytes = 75_000_000L,
        whisperKitModelId = "openai/whisper-tiny.en"
    ),
    BALANCED(
        displayName = "Balanced",
        emoji = "⚖️",
        description = "Great accuracy with good speed",
        sizeDisplay = "~150 MB",
        ggmlFileName = "ggml-base.en.bin",
        ggmlDownloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
        ggmlSizeBytes = 142_000_000L,
        whisperKitModelId = "openai/whisper-base.en"
    ),
    QUALITY(
        displayName = "Quality",
        emoji = "✨",
        description = "Best accuracy for important work",
        sizeDisplay = "~466 MB",
        ggmlFileName = "ggml-small.en.bin",
        ggmlDownloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin",
        ggmlSizeBytes = 466_000_000L,
        whisperKitModelId = "openai/whisper-small.en"
    );

    companion object {
        val DEFAULT = BALANCED
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