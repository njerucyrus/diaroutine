package com.diaroutine.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** The database file in the app's private database directory. */
fun appDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val app = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = app,
        name = app.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
    )
}
