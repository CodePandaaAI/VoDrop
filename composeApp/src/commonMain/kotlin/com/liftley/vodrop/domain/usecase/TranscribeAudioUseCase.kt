package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * Use case for transcribing audio with optional AI polish.
 * Uses kotlin.Result consistently for all operations.
 */
class TranscribeAudioUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val cleanupService: TextCleanupService
) {
    /**
     * Transcribe audio data with optional AI polish.
     * @return Result with transcribed text or error
     */
    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {},
        onIntermediateResult: (String) -> Unit = {}
    ): Result<String> {
        return runCatching {
            onProgress("☁️ Transcribing...")

            when (val stt = sttEngine.transcribe(audioData)) {
                is TranscriptionResult.Success -> {
                    var text = stt.text.trim()
                    onIntermediateResult(text)

                    // Apply AI polish if requested and text is substantial
                    if (mode == TranscriptionMode.WITH_AI_POLISH && text.length > 20) {
                        onProgress("✨ Polishing...")
                        text = applyPolish(text).getOrDefault(text)
                    }
                    
                    text
                }
                is TranscriptionResult.Error -> throw Exception(stt.message)
            }
        }
    }

    /**
     * Improve existing text with AI polish.
     */
    suspend fun improveText(text: String): String? = applyPolish(text).getOrNull()

    private suspend fun applyPolish(text: String): Result<String> {
        if (!cleanupService.isAvailable()) {
            return Result.failure(Exception("Service unavailable"))
        }
        return cleanupService.cleanupText(text, CleanupStyle.INFORMAL)
    }
}