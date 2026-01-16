package com.liftley.vodrop.domain.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.model.Transcription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TranscriptionRepository(private val database: VoDropDatabase) {

    private val queries = database.transcriptionQueries

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

    suspend fun insertTranscription(timestamp: String, text: String) {
        withContext(Dispatchers.IO) {
            queries.insertItem(timestamp, text)
        }
    }

    suspend fun updateTranscription(id: Long, text: String) {
        withContext(Dispatchers.IO) {
            queries.updateText(text, id)
        }
    }

    suspend fun deleteTranscription(id: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteItem(id)
        }
    }
}