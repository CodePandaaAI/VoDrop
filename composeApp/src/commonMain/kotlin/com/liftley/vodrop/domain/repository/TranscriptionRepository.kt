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
     * Returns true if saved, false if text was invalid.
     */
    suspend fun saveTranscription(text: String): Boolean

    suspend fun updateTranscription(id: Long, text: String)
    suspend fun deleteTranscription(id: Long)
}