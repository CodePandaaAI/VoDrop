package com.liftley.vodrop.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Available Whisper models - same GGML models for both Desktop and Android
 */
enum class WhisperModel(
    val displayName: String,
    val emoji: String,
    val description: String,
    val sizeDisplay: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long
) {
    FAST(
        displayName = "Fast",
        emoji = "⚡",
        description = "Quick results, good for short notes",
        sizeDisplay = "~75 MB",
        fileName = "ggml-tiny.en.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
        sizeBytes = 75_000_000L
    ),
    BALANCED(
        displayName = "Balanced",
        emoji = "⚖️",
        description = "Great accuracy with good speed",
        sizeDisplay = "~150 MB",
        fileName = "ggml-base.en.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
        sizeBytes = 142_000_000L
    ),
    QUALITY(
        displayName = "Quality",
        emoji = "✨",
        description = "Best accuracy for important work",
        sizeDisplay = "~466 MB",
        fileName = "ggml-small.en.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin",
        sizeBytes = 466_000_000L
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

    /**
     * ⚡ BATTERY OPTIMIZATION: Check and unload model if inactive for too long.
     * Call this periodically to free memory when app is idle.
     * Default implementation does nothing (for platforms that don't need it).
     */
    fun checkAndUnloadIfInactive() {
        // Default: no-op. Android implementation overrides this.
    }
}

class SpeechToTextException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

expect fun createSpeechToTextEngine(): SpeechToTextEngine