package com.diaroutine.data

import com.diaroutine.data.db.GlucoseDao
import com.diaroutine.data.db.toDomain
import com.diaroutine.data.db.toEntity
import com.diaroutine.domain.model.GlucoseReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class GlucoseRepository(private val dao: GlucoseDao) {
    /** Readings from [from] (inclusive) to [until] (exclusive), oldest first. */
    fun readings(from: Instant, until: Instant): Flow<List<GlucoseReading>> =
        dao.observeRange(from.toEpochMilliseconds(), until.toEpochMilliseconds()).map { list -> list.map { it.toDomain() } }

    /** The latest [limit] readings, newest first. */
    fun recent(limit: Int): Flow<List<GlucoseReading>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun add(reading: GlucoseReading): Long = dao.insert(reading.copy(id = 0).toEntity())

    suspend fun delete(id: Long) = dao.delete(id)
}
