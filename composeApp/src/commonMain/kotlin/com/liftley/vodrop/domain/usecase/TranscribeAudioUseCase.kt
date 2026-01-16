package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.llm.CleanupStyle
import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.preferences.PreferencesManager
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Use case for transcribing audio with style-aware AI cleanup.
 */
class TranscribeAudioUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val textCleanupService: TextCleanupService,
    private val preferencesManager: PreferencesManager
) {

    sealed interface Result {
        data class Success(
            val text: String,
            val usedAI: Boolean
        ) : Result

        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {}
    ): Result {
        return withContext(Dispatchers.Default) {
            try {
                onProgress("☁️ Transcribing...")
                println("☁️ Sending to cloud STT...")

                val sttResult = sttEngine.transcribe(audioData)

                when (sttResult) {
                    is TranscriptionResult.Success -> {
                        var text = sttResult.text.trim()
                        println("✅ Transcription: ${text.take(50)}...")

                        var usedAI = false

                        if (mode == TranscriptionMode.WITH_AI_POLISH &&
                            text.isNotBlank() &&
                            text.length > 20
                        ) {
                            onProgress("$text\n\n✨ Polishing with AI...")
                            println("🤖 Applying AI polish...")

                            val polishedText = applyAIPolish(text)
                            if (polishedText != null) {
                                println("✅ AI polish applied")
                                text = polishedText
                                usedAI = true
                            }
                        }

                        Result.Success(text = text, usedAI = usedAI)
                    }
                    is TranscriptionResult.Error -> {
                        println("❌ Transcription error: ${sttResult.message}")
                        Result.Error(sttResult.message)
                    }
                }
            } catch (e: Exception) {
                println("❌ Exception: ${e.message}")
                Result.Error("Error: ${e.message}")
            }
        }
    }

    suspend fun improveText(text: String): String? {
        return applyAIPolish(text)
    }

    private suspend fun applyAIPolish(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!textCleanupService.isAvailable()) {
                    println("⚠️ AI polish service not available")
                    return@withContext null
                }

                // Get user's preferred style
                val prefs = preferencesManager.getPreferences()
                val style = prefs.cleanupStyle

                println("📝 Using style: ${style.name}")

                val result = textCleanupService.cleanupText(text, style)

                if (result.isSuccess) {
                    result.getOrNull()
                } else {
                    println("⚠️ AI polish failed: ${result.exceptionOrNull()?.message}")
                    null
                }
            } catch (e: Exception) {
                println("❌ AI polish error: ${e.message}")
                null
            }
        }
    }
}