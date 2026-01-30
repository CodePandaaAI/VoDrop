package com.liftley.vodrop.domain.usecase

import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow

/**
 * Use case for managing transcription history (CRUD operations)
 */
class ManageHistoryUseCase(
    private val repository: TranscriptionRepository
) {

    /**
     * Get all transcriptions as a flow
     */
    fun getAllTranscriptions(): Flow<List<Transcription>> {
        return repository.getAllTranscriptions()
    }

    /**
     * Save a new transcription with auto-generated timestamp
     */
    suspend fun saveTranscription(text: String) {
        if (text.isNotBlank() && text != "(No speech detected)") {
            val timestamp = DateTimeUtils.formatCurrentTimestamp()
            repository.insertTranscription(timestamp, text)
        }
    }

    /**
     * Update an existing transcription's text
     */
    suspend fun updateTranscription(id: Long, newText: String) {
        repository.updateTranscription(id, newText)
    }

    /**
     * Delete a transcription by ID
     */
    suspend fun deleteTranscription(id: Long) {
        repository.deleteTranscription(id)
    }
}