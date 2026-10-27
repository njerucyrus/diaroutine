package com.diaroutine.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
