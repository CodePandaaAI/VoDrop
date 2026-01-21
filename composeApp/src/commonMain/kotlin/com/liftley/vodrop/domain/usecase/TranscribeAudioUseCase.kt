package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode

class TranscribeAudioUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val cleanupService: TextCleanupService
) {
    // 1. Rename to avoid conflict with Kotlin's Result class
    sealed interface UseCaseResult {
        data class Success(val text: String, val usedAI: Boolean) : UseCaseResult
        data class Error(val message: String) : UseCaseResult
    }

    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {},
        onIntermediateResult: (String) -> Unit = {}
    ): UseCaseResult {
        return try {
            onProgress("☁️ Transcribing...")

            when (val stt = sttEngine.transcribe(audioData)) {
                is TranscriptionResult.Success -> {
                    var text = stt.text.trim()
                    var usedAI = false

                    onIntermediateResult(text)

                    if (mode == TranscriptionMode.WITH_AI_POLISH && text.length > 20) {
                        onProgress("✨ Polishing...")
                        val polishResult = applyPolish(text)

                        // 2. This fold works because applyPolish returns kotlin.Result<String>
                        polishResult.fold(
                            onSuccess = { polished ->
                                text = polished
                                usedAI = true
                            },
                            onFailure = { e ->
                                println("Polish failed: ${e.message}")
                            }
                        )
                    }
                    UseCaseResult.Success(text, usedAI)
                }
                is TranscriptionResult.Error -> UseCaseResult.Error(stt.message)
            }
        } catch (e: Exception) {
            UseCaseResult.Error("Error: ${e.message}")
        }
    }

    suspend fun improveText(text: String): String? = applyPolish(text).getOrNull()

    // 3. Explicitly return kotlin.Result<String>
    private suspend fun applyPolish(text: String): Result<String> {
        if (!cleanupService.isAvailable()) return Result.failure(Exception("Service unavailable"))
        return cleanupService.cleanupText(text, CleanupStyle.INFORMAL)
    }
}