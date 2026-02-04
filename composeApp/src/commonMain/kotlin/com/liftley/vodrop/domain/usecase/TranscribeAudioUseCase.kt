package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.cloud.CloudTranscriptionService
import com.liftley.vodrop.data.cloud.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * **Transcription Result Container**
 * Holds one or both versions of the transcribed text.
 */
data class TranscriptionTexts(
    val original: String,
    val polished: String? = null
)

/**
 * **Transcribe Audio Use Case**
 * 
 * Orchestrates the "Record -> Transcribe -> Polish" business logic.
 * Decides *when* to apply AI polish based on user preferences and text content.
 * 
 * **Logic:**
 * 1. Call Cloud Service to Transcribe Audio (Chirp 3).
 * 2. If [mode] is [TranscriptionMode.WITH_AI_POLISH] AND text > 20 chars:
 *    - Call Cloud Service to Polish Text (Gemini 3 Flash).
 * 3. Return combined result.
 */
class TranscribeAudioUseCase(
    private val cloudService: CloudTranscriptionService
) {
    /**
     * Executes the transcription pipeline.
     * 
     * @param audioData Raw PCM audio bytes.
     * @param mode User's selected mode (Standard or AI Polish).
     * @param onProgress Callback to update UI status messages (e.g. "Uploading...", "Polishing...").
     */
    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {}
    ): Result<TranscriptionTexts> {
        return runCatching {
            onProgress("☁️ Transcribing...")

            when (val result = cloudService.transcribe(audioData)) {
                is TranscriptionResult.Success -> {
                    val originalText = result.text.trim()

                    // Optimization: Don't polish very short phrases ("Hello", "Testing") 
                    // even if mode is active, to save time/cost.
                    val polishedText = if (mode == TranscriptionMode.WITH_AI_POLISH && originalText.length > 20) {
                        onProgress("✨ Polishing...")
                        cloudService.polish(originalText)
                    } else {
                        null
                    }
                    
                    TranscriptionTexts(original = originalText, polished = polishedText)
                }
                is TranscriptionResult.Error -> throw Exception(result.message)
            }
        }
    }

    /**
     * **Re-Polish Feature**
     * 
     * Allows the user to apply AI polish to an existing "Standard" transcription later.
     */
    suspend fun improveText(text: String): String? = cloudService.polish(text)
}