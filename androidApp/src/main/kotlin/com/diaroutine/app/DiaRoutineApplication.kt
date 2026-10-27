package com.diaroutine.app

import android.app.Application
import android.content.Context
import com.diaroutine.app.backup.AndroidCloudBackup
import com.diaroutine.app.backup.DriveAuthorization
import com.diaroutine.app.backup.RecoveryKeyVault
import com.diaroutine.app.premium.AndroidPremiumStore
import com.diaroutine.app.reminders.AndroidReminderScheduler
import com.diaroutine.app.reminders.Notifications
import com.diaroutine.data.db.appDatabaseBuilder
import com.diaroutine.data.db.buildAppDatabase

/** Builds the shared app's object graph once per process, with Android's platform pieces. */
class DiaRoutineApplication : Application() {
    lateinit var container: AppContainer
        private set

    lateinit var platform: AndroidPlatformServices
        private set

    lateinit var premium: AndroidPremiumStore
        private set

    lateinit var driveAuth: DriveAuthorization
        private set

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatformServices(this)
        premium = AndroidPremiumStore(this)
        driveAuth = DriveAuthorization(this)
        container = AppContainer(
            database = buildAppDatabase(appDatabaseBuilder(this)),
            platform = platform,
            reminderScheduler = AndroidReminderScheduler(this),
            premium = premium,
            cloudBackup = { codec -> AndroidCloudBackup(this, codec, driveAuth, RecoveryKeyVault(this)) },
        )
        Notifications.createChannels(this)
        premium.refresh()
        container.startReminderSync()
    }
}

val Context.diaRoutine: DiaRoutineApplication get() = applicationContext as DiaRoutineApplication
