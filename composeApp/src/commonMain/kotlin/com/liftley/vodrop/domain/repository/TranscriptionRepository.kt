package com.liftley.vodrop.domain.repository

import com.liftley.vodrop.domain.model.Transcription
import kotlinx.coroutines.flow.Flow

/**
 * **Transcription Repository Interface**
 * 
 * Defines the contract for data persistence operations.
 * Allows the Domain layer to interact with the database without knowing the implementation (SQLDelight).
 */
interface TranscriptionRepository {
    
    /**
     * Observes all saved transcriptions as a reactive stream.
     * Automatically emits new lists when the database changes.
     */
    fun getAllTranscriptions(): Flow<List<Transcription>>

    /**
     * Persists a newly completed transcription.
     * 
     * @param originalText The raw output from the STT engine.
     * @param polishedText The optional AI-refined text (can be null if user chose Standard mode).
     * @return true if save was successful, false otherwise.
     */
    suspend fun saveTranscription(originalText: String, polishedText: String? = null): Boolean

    /** 
     * Updates the raw text field of an existing entry.
     * Used when the user manually edits the "Original" text.
     */
    suspend fun updateOriginalText(id: Long, text: String)

    /** 
     * Updates the polished text field of an existing entry.
     * Used when the user manually edits the "Polished" text OR triggers a re-polish.
     */
    suspend fun updatePolishedText(id: Long, text: String)

    /**
     * Permanently removes a transcription entry.
     */
    suspend fun deleteTranscription(id: Long)
}