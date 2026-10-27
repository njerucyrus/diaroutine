package com.diaroutine.data

import com.diaroutine.core.time.epochDay
import com.diaroutine.core.time.minuteOfDay
import com.diaroutine.data.db.DoseLogDao
import com.diaroutine.data.db.DoseLogEntity
import com.diaroutine.data.db.MedicationDao
import com.diaroutine.data.db.toDomain
import com.diaroutine.data.db.toEntity
import com.diaroutine.domain.model.DoseLog
import com.diaroutine.domain.model.DoseStatus
import com.diaroutine.domain.model.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Medications and the doses logged against them. Taking a dose draws down the
 * tracked supply and undoing it puts the supply back.
 */
class MedicationRepository(
    private val medications: MedicationDao,
    private val doses: DoseLogDao,
    private val clock: Clock,
    private val zone: () -> TimeZone,
) {
    /** Every medication, including stopped ones (their history still counts). */
    val all: Flow<List<Medication>> = medications.observeAll().map { list -> list.map { it.toDomain() } }

    private val writes = Mutex()

    fun doseLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        doses.observeRange(from.epochDay, to.epochDay).map { list -> list.map { it.toDomain() } }

    suspend fun save(medication: Medication): Long = writes.withLock {
        val entity = medication.toEntity()
        if (medication.id == 0L) medications.insert(entity) else entity.id.also { medications.update(entity) }
    }

    /**
     * Takes the medication out of the routine from [today] on. One started today
     * has no history worth keeping, so it's removed outright.
     */
    suspend fun stop(id: Long, today: LocalDate) = writes.withLock {
        val med = medications.get(id)?.toDomain() ?: return@withLock
        if (med.startDate >= today) {
            doses.deleteForMedication(id)
            medications.delete(id)
        } else {
            medications.end(id, today.epochDay)
        }
    }

    suspend fun setSupply(id: Long, amount: Int?) = writes.withLock {
        medications.setSupply(id, amount?.coerceAtLeast(0))
    }

    suspend fun markTaken(medicationId: Long, date: LocalDate, time: LocalTime) =
        answer(medicationId, date, time, DoseStatus.Taken)

    suspend fun markSkipped(medicationId: Long, date: LocalDate, time: LocalTime) =
        answer(medicationId, date, time, DoseStatus.Skipped)

    /** Clears the answer to a scheduled dose, putting back supply if it had been taken. */
    suspend fun clearDose(medicationId: Long, date: LocalDate, time: LocalTime) = writes.withLock {
        val existing = doses.findScheduled(medicationId, date.epochDay, time.minuteOfDay) ?: return@withLock
        doses.delete(existing.id)
        if (existing.status == DoseStatus.Taken.name) restoreSupply(medicationId)
    }

    suspend fun logAsNeeded(medicationId: Long) = writes.withLock {
        val now = clock.now()
        doses.insert(
            DoseLogEntity(
                medicationId = medicationId,
                epochDay = now.toLocalDateTime(zone()).date.epochDay,
                scheduledMinute = null,
                status = DoseStatus.Taken.name,
                loggedAtMillis = now.toEpochMilliseconds(),
            ),
        )
        drawSupply(medicationId)
    }

    suspend fun deleteLog(logId: Long) = writes.withLock {
        val existing = doses.get(logId) ?: return@withLock
        doses.delete(logId)
        if (existing.status == DoseStatus.Taken.name) restoreSupply(existing.medicationId)
    }

    private suspend fun answer(medicationId: Long, date: LocalDate, time: LocalTime, status: DoseStatus) = writes.withLock {
        val existing = doses.findScheduled(medicationId, date.epochDay, time.minuteOfDay)
        if (existing?.status == status.name) return@withLock
        if (existing != null) {
            doses.delete(existing.id)
            if (existing.status == DoseStatus.Taken.name) restoreSupply(medicationId)
        }
        doses.insert(
            DoseLogEntity(
                medicationId = medicationId,
                epochDay = date.epochDay,
                scheduledMinute = time.minuteOfDay,
                status = status.name,
                loggedAtMillis = clock.now().toEpochMilliseconds(),
            ),
        )
        if (status == DoseStatus.Taken) drawSupply(medicationId)
    }

    private suspend fun drawSupply(medicationId: Long) {
        val med = medications.get(medicationId) ?: return
        medications.adjustSupply(medicationId, -med.amountPerDose)
    }

    private suspend fun restoreSupply(medicationId: Long) {
        val med = medications.get(medicationId) ?: return
        medications.adjustSupply(medicationId, med.amountPerDose)
    }
}
