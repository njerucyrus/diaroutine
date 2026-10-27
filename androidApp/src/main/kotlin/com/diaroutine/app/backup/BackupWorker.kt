package com.diaroutine.app.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diaroutine.app.diaRoutine
import com.diaroutine.app.premium.AndroidPremiumStore
import java.util.concurrent.TimeUnit

object BackupScheduler {
    private const val WORK = "weekly-backup"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/** The weekly backup. Skips quietly while Premium is off; the schedule stays for when it's back. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AndroidPremiumStore.isPremium(applicationContext)) return Result.success()
        val backup = applicationContext.diaRoutine.container.backup as AndroidCloudBackup
        return when {
            backup.backUp(interactive = false) -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
