package com.diaroutine.data

import com.diaroutine.data.db.AppointmentDao
import com.diaroutine.data.db.toDomain
import com.diaroutine.data.db.toEntity
import com.diaroutine.domain.model.Appointment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppointmentRepository(private val dao: AppointmentDao) {
    /** Every appointment, soonest first. */
    val all: Flow<List<Appointment>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(appointment: Appointment): Long {
        val entity = appointment.toEntity()
        return if (appointment.id == 0L) dao.insert(entity) else entity.id.also { dao.update(entity) }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
