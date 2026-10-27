package com.diaroutine.backup

import com.diaroutine.data.db.AppointmentEntity
import com.diaroutine.data.db.BackupDao
import com.diaroutine.data.db.DailyHabitEntity
import com.diaroutine.data.db.DoseLogEntity
import com.diaroutine.data.db.GlucoseReadingEntity
import com.diaroutine.data.db.MedicationEntity
import com.diaroutine.data.db.ProfileEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Everything a person has entered: the contents of a backup before it is encrypted. */
@Serializable
data class BackupData(
    val format: Int = BackupCodec.FORMAT,
    val createdAtMillis: Long,
    val appVersion: String,
    val profiles: List<ProfileEntity> = emptyList(),
    val medications: List<MedicationEntity> = emptyList(),
    val doseLogs: List<DoseLogEntity> = emptyList(),
    val readings: List<GlucoseReadingEntity> = emptyList(),
    val habits: List<DailyHabitEntity> = emptyList(),
    val appointments: List<AppointmentEntity> = emptyList(),
)

class UnsupportedBackupException : Exception("This backup was made by a newer version of DiaRoutine")

/** Turns the database into backup bytes (JSON) and back. Encryption is the platform's job. */
class BackupCodec(
    private val dao: BackupDao,
    private val clock: Clock,
    private val appVersion: String,
) {
    suspend fun export(): ByteArray = encode(
        BackupData(
            createdAtMillis = clock.now().toEpochMilliseconds(),
            appVersion = appVersion,
            profiles = dao.profiles(),
            medications = dao.medications(),
            doseLogs = dao.doseLogs(),
            readings = dao.readings(),
            habits = dao.habits(),
            appointments = dao.appointments(),
        ),
    )

    /** Replaces everything on this device with the backup's contents, all at once. */
    suspend fun import(bytes: ByteArray) {
        val data = decode(bytes)
        dao.replaceAll(data.profiles, data.medications, data.doseLogs, data.readings, data.habits, data.appointments)
    }

    companion object {
        const val FORMAT = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(data: BackupData): ByteArray = json.encodeToString(BackupData.serializer(), data).encodeToByteArray()

        fun decode(bytes: ByteArray): BackupData {
            val data = json.decodeFromString(BackupData.serializer(), bytes.decodeToString())
            if (data.format > FORMAT) throw UnsupportedBackupException()
            return data
        }
    }
}
