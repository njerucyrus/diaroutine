package com.diaroutine.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): MedicationEntity?

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE medications SET endEpochDay = :epochDay WHERE id = :id")
    suspend fun end(id: Long, epochDay: Long)

    @Query("UPDATE medications SET supplyRemaining = MAX(0, supplyRemaining + :delta) WHERE id = :id AND supplyRemaining IS NOT NULL")
    suspend fun adjustSupply(id: Long, delta: Int)

    @Query("UPDATE medications SET supplyRemaining = :amount WHERE id = :id")
    suspend fun setSupply(id: Long, amount: Int?)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE epochDay BETWEEN :fromDay AND :toDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId AND epochDay = :epochDay AND scheduledMinute = :minute LIMIT 1")
    suspend fun findScheduled(medicationId: Long, epochDay: Long, minute: Int): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun get(id: Long): DoseLogEntity?

    @Insert
    suspend fun insert(log: DoseLogEntity): Long

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)
}

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_readings WHERE atMillis >= :fromMillis AND atMillis < :toMillis ORDER BY atMillis")
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings ORDER BY atMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GlucoseReadingEntity>>

    @Insert
    suspend fun insert(reading: GlucoseReadingEntity): Long

    @Query("DELETE FROM glucose_readings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM daily_habits WHERE epochDay BETWEEN :fromDay AND :toDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<DailyHabitEntity>>

    @Query("SELECT * FROM daily_habits WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyHabitEntity?

    @Upsert
    suspend fun upsert(habit: DailyHabitEntity)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY dateTime")
    fun observeAll(): Flow<List<AppointmentEntity>>

    @Insert
    suspend fun insert(appointment: AppointmentEntity): Long

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Reads every table for a backup, and swaps them all for a backup's contents in one transaction. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM profile") abstract suspend fun profiles(): List<ProfileEntity>
    @Query("SELECT * FROM medications") abstract suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM dose_logs") abstract suspend fun doseLogs(): List<DoseLogEntity>
    @Query("SELECT * FROM glucose_readings") abstract suspend fun readings(): List<GlucoseReadingEntity>
    @Query("SELECT * FROM daily_habits") abstract suspend fun habits(): List<DailyHabitEntity>
    @Query("SELECT * FROM appointments") abstract suspend fun appointments(): List<AppointmentEntity>

    @Query("DELETE FROM profile") protected abstract suspend fun clearProfiles()
    @Query("DELETE FROM medications") protected abstract suspend fun clearMedications()
    @Query("DELETE FROM dose_logs") protected abstract suspend fun clearDoseLogs()
    @Query("DELETE FROM glucose_readings") protected abstract suspend fun clearReadings()
    @Query("DELETE FROM daily_habits") protected abstract suspend fun clearHabits()
    @Query("DELETE FROM appointments") protected abstract suspend fun clearAppointments()

    @Insert protected abstract suspend fun insertProfiles(rows: List<ProfileEntity>)
    @Insert protected abstract suspend fun insertMedications(rows: List<MedicationEntity>)
    @Insert protected abstract suspend fun insertDoseLogs(rows: List<DoseLogEntity>)
    @Insert protected abstract suspend fun insertReadings(rows: List<GlucoseReadingEntity>)
    @Insert protected abstract suspend fun insertHabits(rows: List<DailyHabitEntity>)
    @Insert protected abstract suspend fun insertAppointments(rows: List<AppointmentEntity>)

    @Transaction
    open suspend fun replaceAll(
        profiles: List<ProfileEntity>,
        medications: List<MedicationEntity>,
        doseLogs: List<DoseLogEntity>,
        readings: List<GlucoseReadingEntity>,
        habits: List<DailyHabitEntity>,
        appointments: List<AppointmentEntity>,
    ) {
        clearProfiles()
        clearMedications()
        clearDoseLogs()
        clearReadings()
        clearHabits()
        clearAppointments()
        insertProfiles(profiles)
        insertMedications(medications)
        insertDoseLogs(doseLogs)
        insertReadings(readings)
        insertHabits(habits)
        insertAppointments(appointments)
    }
}
