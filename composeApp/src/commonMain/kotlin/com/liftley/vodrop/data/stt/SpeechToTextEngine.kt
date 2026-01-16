package com.liftley.vodrop.data.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Available Whisper models - same GGML models for both Desktop and Android
 */
enum class WhisperModel(
    val displayName: String,
    val emoji: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val isProOnly: Boolean,
    val description: String,
    val sizeDisplay: String
) {
    BALANCED(
        displayName = "Balanced",
        emoji = "⚖️",
        fileName = "ggml-base-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sizeBytes = 57_000_000L,
        isProOnly = false,
        description = "Good balance of speed and accuracy",
        sizeDisplay = "57 MB"
    ),
    QUALITY(
        displayName = "Quality",
        emoji = "✨",
        fileName = "ggml-small-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sizeBytes = 181_000_000L,
        isProOnly = true,
        description = "Best accuracy, slower processing",
        sizeDisplay = "181 MB"
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