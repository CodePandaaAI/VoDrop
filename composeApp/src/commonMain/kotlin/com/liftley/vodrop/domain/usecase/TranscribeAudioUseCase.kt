package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.cloud.CloudTranscriptionService
import com.liftley.vodrop.data.cloud.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * Result of transcription with both original and polished text.
 */
data class TranscriptionTexts(
    val original: String,
    val polished: String? = null
)

/**
 * Use case for transcribing audio with optional AI polish.
 * 
 * Single dependency: CloudTranscriptionService handles everything.
 */
class TranscribeAudioUseCase(
    private val cloudService: CloudTranscriptionService
) {
    /**
     * Transcribe audio data with optional AI polish.
     * @return Result with both original and polished text
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

                    // Apply AI polish if requested and text is substantial
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
     * Improve existing text with AI polish.
     * Used for re-polishing history items.
     */
    suspend fun improveText(text: String): String? = cloudService.polish(text)
}