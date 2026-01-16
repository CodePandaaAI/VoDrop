package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.data.llm.TextCleanupService
import com.liftley.vodrop.data.stt.GroqWhisperService
import com.liftley.vodrop.data.stt.SpeechToTextEngine
import com.liftley.vodrop.data.stt.TranscriptionResult
import com.liftley.vodrop.ui.main.TranscriptionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Use case for transcribing audio with optional AI cleanup.
 * Orchestrates STT engine selection and LLM processing based on mode.
 */
class TranscribeAudioUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val groqService: GroqWhisperService,
    private val textCleanupService: TextCleanupService
) {

    /**
     * Result of a transcription operation
     */
    sealed interface Result {
        data class Success(
            val text: String,
            val usedCloud: Boolean,
            val usedAI: Boolean
        ) : Result

        data class Error(val message: String) : Result
    }

    /**
     * Transcribe audio data based on the selected mode
     *
     * @param audioData Raw PCM audio bytes (16kHz, mono, 16-bit)
     * @param mode Transcription mode (offline, offline+AI, cloud+AI)
     * @param onProgress Callback for progress updates
     * @return Transcription result with metadata
     */
    suspend operator fun invoke(
        audioData: ByteArray,
        mode: TranscriptionMode,
        onProgress: (String) -> Unit = {}
    ): Result {
        return withContext(Dispatchers.Default) {
            try {
                // Step 1: Choose STT engine
                val sttResult = when (mode) {
                    TranscriptionMode.CLOUD_WITH_AI -> {
                        onProgress("☁️ Transcribing with cloud...")
                        println("☁️ Using Groq Whisper (cloud)")
                        withContext(Dispatchers.IO) { groqService.transcribe(audioData) }
                    }
                    else -> {
                        onProgress("📱 Transcribing locally...")
                        println("📱 Using Whisper.cpp (offline)")
                        sttEngine.transcribe(audioData)
                    }
                }

                // Step 2: Process STT result
                when (sttResult) {
                    is TranscriptionResult.Success -> {
                        var text = sttResult.text.trim()
                        println("✅ Transcription result: $text")

                        var usedAI = false

                        // Step 3: Apply AI cleanup if mode requires it
                        if (mode != TranscriptionMode.OFFLINE_ONLY &&
                            text.isNotBlank() &&
                            text.length > 30
                        ) {
                            onProgress("$text\n\n⏳ Improving with AI...")
                            println("🤖 Applying Gemini LLM cleanup...")

                            val improvedText = applyAICleanup(text)
                            if (improvedText != null) {
                                println("✅ Gemini improvement applied")
                                text = improvedText
                                usedAI = true
                            } else {
                                println("⚠️ Gemini improvement failed, using original")
                            }
                        } else {
                            println("⏭️ Skipping AI cleanup (mode=$mode, length=${text.length})")
                        }

                        Result.Success(
                            text = text,
                            usedCloud = mode == TranscriptionMode.CLOUD_WITH_AI,
                            usedAI = usedAI
                        )
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

    /**
     * Apply AI text cleanup using the injected service
     */
    private suspend fun applyAICleanup(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!textCleanupService.isAvailable()) {
                    println("⚠️ Text cleanup service not available")
                    return@withContext null
                }

                val result = textCleanupService.cleanupText(text)

                if (result.isSuccess) {
                    result.getOrNull()
                } else {
                    println("⚠️ Cleanup failed: ${result.exceptionOrNull()?.message}")
                    null
                }
            } catch (e: Exception) {
                println("❌ AI cleanup error: ${e.message}")
                null
            }
        }
    }
}