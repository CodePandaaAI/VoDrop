package com.liftley.vodrop.domain.repository

import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.IO

class TranscriptionRepositoryImpl(
    private val database: VoDropDatabase
) : TranscriptionRepository {

    private val queries = database.transcriptionQueries

    override fun getAllTranscriptions(): Flow<List<Transcription>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities ->
                entities.map { entity ->
                    Transcription(
                        id = entity.id,
                        timestamp = entity.timestamp,
                        text = entity.text
                    )
                }
            }
    }

    override suspend fun saveTranscription(text: String): Boolean {
        // Validation logic moved from UseCase
        if (text.isBlank() || text == "(No speech detected)") {
            return false
        }

        return withContext(Dispatchers.IO) {
            val timestamp = DateTimeUtils.formatCurrentTimestamp()
            queries.insertItem(timestamp, text)
            true
        }
    }

    override suspend fun updateTranscription(id: Long, text: String) {
        withContext(Dispatchers.IO) {
            queries.updateText(text, id)
        }
    }

    override suspend fun deleteTranscription(id: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteItem(id)
        }
    }
}