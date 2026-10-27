package com.diaroutine.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * The app's tables. Each entity is also @Serializable: a backup is these rows as JSON
 * (see BackupData), so a column added later needs a default for older backups to load.
 */

/** The single profile row (id 0). */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val glucoseUnit: String,
    val targetLowMgDl: Int,
    val targetHighMgDl: Int,
    val waterGoalGlasses: Int,
    val stepGoal: Int,
    val walkGoalMinutes: Int,
    val walkMinuteOfDay: Int?,
    /** "420:Fasting,1290:Bedtime" — minute of day and meal context. */
    val glucoseChecks: String,
    val remindersEnabled: Boolean,
    val caregiverEnabled: Boolean,
    val caregiverName: String,
    val caregiverContact: String,
    val onboarded: Boolean,
    // Added in version 2. Defaults let older backups load.
    @ColumnInfo(defaultValue = "0") val appLockEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val appLockAfterSeconds: Int = 0,
    @ColumnInfo(defaultValue = "0") val tourCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "30") val followUpMinutes: Int = 30,
)

@Serializable
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strength: String,
    val form: String,
    val amountPerDose: Int,
    /** Minutes of day, comma separated: "480,1200". */
    val times: String,
    val asNeeded: Boolean,
    val instructions: String,
    val supplyRemaining: Int?,
    val refillAt: Int,
    val startEpochDay: Long,
    val endEpochDay: Long?,
)

@Serializable
@Entity(
    tableName = "dose_logs",
    indices = [
        Index(value = ["medicationId", "epochDay", "scheduledMinute"], unique = true),
        Index(value = ["epochDay"]),
    ],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val epochDay: Long,
    /** Null for as-needed doses, which may repeat in a day. */
    val scheduledMinute: Int?,
    val status: String,
    val loggedAtMillis: Long,
)

@Serializable
@Entity(tableName = "glucose_readings", indices = [Index(value = ["atMillis"])])
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val mgDl: Int,
    val context: String,
    val note: String,
)

@Serializable
@Entity(tableName = "daily_habits")
data class DailyHabitEntity(
    @PrimaryKey val epochDay: Long,
    val waterGlasses: Int,
    val steps: Int,
    val activeMinutes: Int,
)

@Serializable
@Entity(tableName = "appointments", indices = [Index(value = ["dateTime"])])
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val withWhom: String,
    /** Local wall-clock time, ISO 8601: "2026-10-30T10:30". */
    val dateTime: String,
    val notes: String,
    val remindBeforeMinutes: Int,
)
