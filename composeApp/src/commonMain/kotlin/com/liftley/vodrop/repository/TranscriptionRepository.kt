package com.liftley.vodrop.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.model.Transcription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing transcription data persistence.
 */
class TranscriptionRepository(private val database: VoDropDatabase) {

    private val queries = database.transcriptionQueries

    /**
     * Retrieves all transcriptions as a reactive Flow.
     */
    fun getAllTranscriptions(): Flow<List<Transcription>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { entity ->
                    Transcription(
                        id = entity.id,
                        timestamp = entity.timestamp,
                        text = entity.text
                    )
                }
            }
    }

    /**
     * Inserts a new transcription into the database.
     */
    suspend fun insertTranscription(timestamp: String, text: String) {
        withContext(Dispatchers.IO) {
            queries.insertItem(timestamp, text)
        }
    }

    /**
     * Deletes a transcription by its ID.
     */
    suspend fun deleteTranscription(id: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteItem(id)
        }
    }
}