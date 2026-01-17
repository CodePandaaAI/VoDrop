package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * Use case for transcribing audio to text.
 * 
 * v1: Uses INFORMAL cleanup style (hardcoded, no user selection).
 */
class TranscribeAudioUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val cleanupService: TextCleanupService
) {
    sealed interface Result {
        data class Success(val text: String, val usedAI: Boolean) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {}
    ): Result {
        return try {
            onProgress("☁️ Transcribing...")

            when (val stt = sttEngine.transcribe(audioData)) {
                is TranscriptionResult.Success -> {
                    var text = stt.text.trim()
                    var usedAI = false

                    if (mode == TranscriptionMode.WITH_AI_POLISH && text.length > 20) {
                        onProgress("✨ Polishing...")
                        val polished = applyPolish(text)
                        if (polished != null) {
                            text = polished
                            usedAI = true
                        }
                    }
                    Result.Success(text, usedAI)
                }
                is TranscriptionResult.Error -> Result.Error(stt.message)
            }
        } catch (e: Exception) {
            Result.Error("Error: ${e.message}")
        }
    }

    suspend fun improveText(text: String): String? = applyPolish(text)

    private suspend fun applyPolish(text: String): String? {
        if (!cleanupService.isAvailable()) return null
        // v1: Hardcode to INFORMAL style (simple, natural cleanup)
        return cleanupService.cleanupText(text, CleanupStyle.INFORMAL).getOrNull()
    }
}