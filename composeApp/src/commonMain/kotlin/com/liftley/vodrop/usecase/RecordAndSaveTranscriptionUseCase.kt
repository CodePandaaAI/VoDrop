package com.liftley.vodrop.usecase

import com.liftley.vodrop.repository.TranscriptionRepository
import com.liftley.vodrop.stt.SpeechToTextEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Use case that orchestrates recording audio and saving the transcription.
 */
class RecordAndSaveTranscriptionUseCase(
    private val sttEngine: SpeechToTextEngine,
    private val repository: TranscriptionRepository
) {

    /**
     * Starts the recording process.
     */
    fun startRecording() {
        sttEngine.startRecording()
    }

    /**
     * Stops recording and saves the transcription to the database.
     * @param timestamp The timestamp to associate with this transcription.
     * @return Flow emitting transcribed text chunks.
     */
    fun stopRecordingAndSave(timestamp: String): Flow<String> {
        val textBuilder = StringBuilder()

        return sttEngine.stopRecording()
            .onEach { chunk ->
                textBuilder.append(chunk)
            }
            .onCompletion {
                val fullText = textBuilder.toString()
                if (fullText.isNotBlank()) {
                    repository.insertTranscription(timestamp, fullText)
                }
            }
    }
}