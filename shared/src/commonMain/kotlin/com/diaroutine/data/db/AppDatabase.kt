package com.diaroutine.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class,
        DoseLogEntity::class,
        GlucoseReadingEntity::class,
        DailyHabitEntity::class,
        AppointmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: app lock settings and the app tour flag on the profile.
        AutoMigration(from = 1, to = 2),
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun glucoseDao(): GlucoseDao
    abstract fun habitDao(): HabitDao
    abstract fun appointmentDao(): AppointmentDao

    companion object {
        const val FILE_NAME = "diaroutine.db"
    }
}

// Room's compiler generates the actual implementation for each platform.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Finishes a platform's builder (which knows where the file lives) the same way everywhere. */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
