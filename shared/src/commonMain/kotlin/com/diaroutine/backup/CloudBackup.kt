package com.diaroutine.backup

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

data class BackupStatus(
    /** The connected Google account, once Drive access is granted. */
    val account: String? = null,
    val connected: Boolean = false,
    val hasRecoveryKey: Boolean = false,
    val weekly: Boolean = false,
    val lastBackupAt: Instant? = null,
    /** Why the last automatic or manual backup failed, in words for the person. */
    val problem: String? = null,
    val working: Boolean = false,
)

data class CloudBackupInfo(
    val savedAt: Instant,
    val sizeBytes: Long,
    /** False when this phone's recovery key isn't the one the backup was made with; null when unknown. */
    val madeWithThisKey: Boolean?,
)

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data object WrongKey : RestoreResult
    data object NoBackup : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * Encrypted backup to the person's cloud storage — Google Drive's hidden app folder on
 * Android. Backups are encrypted on the device with a key derived from the recovery key,
 * which never leaves the device except when the person writes it down.
 */
interface CloudBackup {
    val status: StateFlow<BackupStatus>

    /** Asks for access to the app's Drive folder; true once granted. */
    suspend fun connect(): Boolean

    /** Stops backing up and forgets the account and recovery key on this device. The backup itself stays. */
    suspend fun disconnect()

    /** Makes a new recovery key, keeps it securely on this device, and returns it for the person to save. */
    suspend fun createRecoveryKey(): String

    /** The recovery key kept on this device, for showing again. */
    suspend fun recoveryKey(): String?

    fun setWeekly(enabled: Boolean)

    suspend fun backUpNow(): Boolean

    suspend fun findBackup(): CloudBackupInfo?

    /** Replaces this device's data with the backup, if [recoveryKey] opens it. */
    suspend fun restore(recoveryKey: String): RestoreResult

    suspend fun deleteBackup(): Boolean
}
