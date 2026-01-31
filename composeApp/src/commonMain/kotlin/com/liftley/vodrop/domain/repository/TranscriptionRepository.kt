package com.liftley.vodrop.domain.repository

import com.liftley.vodrop.domain.model.Transcription
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for transcription data operations.
 * Defines the contract - implementation is in data layer.
 */
interface TranscriptionRepository {
    fun getAllTranscriptions(): Flow<List<Transcription>>

    /**
     * Save a transcription with auto-generated timestamp.
     * @param originalText The raw STT output
     * @param polishedText Optional AI-polished version
     * @return true if saved, false if text was invalid
     */
    suspend fun saveTranscription(originalText: String, polishedText: String? = null): Boolean

    /** Update the original text (user manual edits) */
    suspend fun updateOriginalText(id: Long, text: String)

    /** Update the polished text (AI re-polish) */
    suspend fun updatePolishedText(id: Long, text: String)

    suspend fun deleteTranscription(id: Long)
}