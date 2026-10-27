package com.diaroutine.data

import com.diaroutine.core.time.epochDay
import com.diaroutine.data.db.HabitDao
import com.diaroutine.data.db.toDomain
import com.diaroutine.data.db.toEntity
import com.diaroutine.domain.model.DailyHabit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/** Water, steps and active minutes, one row per day. */
class HabitRepository(private val dao: HabitDao) {
    private val writes = Mutex()

    fun habits(from: LocalDate, to: LocalDate): Flow<List<DailyHabit>> =
        dao.observeRange(from.epochDay, to.epochDay).map { list -> list.map { it.toDomain() } }

    suspend fun addWater(date: LocalDate, glasses: Int) = edit(date) {
        it.copy(waterGlasses = (it.waterGlasses + glasses).coerceIn(0, MAX_GLASSES))
    }

    suspend fun setActivity(date: LocalDate, steps: Int, activeMinutes: Int) = edit(date) {
        it.copy(steps = steps.coerceIn(0, MAX_STEPS), activeMinutes = activeMinutes.coerceIn(0, MAX_MINUTES))
    }

    private suspend fun edit(date: LocalDate, transform: (DailyHabit) -> DailyHabit) = writes.withLock {
        val current = dao.get(date.epochDay)?.toDomain() ?: DailyHabit(date)
        dao.upsert(transform(current).toEntity())
    }

    companion object {
        const val MAX_GLASSES = 30
        const val MAX_STEPS = 100_000
        const val MAX_MINUTES = 24 * 60
    }
}
