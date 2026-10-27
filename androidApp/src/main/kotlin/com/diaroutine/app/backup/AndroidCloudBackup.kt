package com.diaroutine.app.backup

import android.content.Context
import androidx.core.content.edit
import com.diaroutine.backup.BackupCodec
import com.diaroutine.backup.BackupStatus
import com.diaroutine.backup.CloudBackup
import com.diaroutine.backup.CloudBackupInfo
import com.diaroutine.backup.RecoveryKey
import com.diaroutine.backup.RestoreResult
import com.diaroutine.backup.UnsupportedBackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Encrypted backup to Google Drive: the database is exported, encrypted on the phone
 * with the recovery key, and uploaded to the app's hidden Drive folder, replacing the
 * previous backup. Restoring needs the same Google account and the recovery key.
 */
class AndroidCloudBackup(
    private val context: Context,
    private val codec: BackupCodec,
    private val auth: DriveAuthorization,
    private val vault: RecoveryKeyVault,
    private val clock: Clock = Clock.System,
) : CloudBackup {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val mutableStatus = MutableStateFlow(savedStatus())
    override val status: StateFlow<BackupStatus> = mutableStatus.asStateFlow()
    private val oneAtATime = Mutex()

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = auth.token(interactive = true)
            val account = runCatching { DriveClient(token).accountEmail() }.getOrNull()
            prefs.edit {
                putBoolean(CONNECTED, true)
                putString(ACCOUNT, account)
                remove(PROBLEM)
            }
            publish()
            true
        } catch (e: DriveAuthException) {
            problem(e.message)
            false
        }
    }

    override suspend fun disconnect() {
        BackupScheduler.cancel(context)
        vault.clear()
        prefs.edit { clear() }
        publish()
    }

    override suspend fun createRecoveryKey(): String {
        val key = ByteArray(RecoveryKey.BYTES).also(SecureRandom()::nextBytes)
        vault.store(key)
        prefs.edit { putString(KEY_ID, BackupCrypto.keyId(key)) }
        publish()
        return RecoveryKey.format(key)
    }

    override suspend fun recoveryKey(): String? = vault.load()?.let(RecoveryKey::format)

    override fun setWeekly(enabled: Boolean) {
        prefs.edit { putBoolean(WEEKLY, enabled) }
        if (enabled) BackupScheduler.schedule(context) else BackupScheduler.cancel(context)
        publish()
    }

    override suspend fun backUpNow(): Boolean = backUp(interactive = true)

    /** Used by the weekly job too, which can't ask the person anything. */
    suspend fun backUp(interactive: Boolean): Boolean = oneAtATime.withLock {
        val key = vault.load() ?: return problem("Set up backup again to create a recovery key.")
        mutableStatus.update { it.copy(working = true) }
        try {
            withContext(Dispatchers.IO) {
                val drive = DriveClient(auth.token(interactive))
                val sealed = BackupCrypto.seal(codec.export(), key)
                drive.upload(drive.find()?.id, sealed, BackupCrypto.keyId(key))
            }
            prefs.edit {
                putLong(LAST_BACKUP, clock.now().toEpochMilliseconds())
                remove(PROBLEM)
            }
            publish()
            true
        } catch (e: DriveAuthException) {
            problem(e.message)
        } catch (_: IOException) {
            problem("Couldn't reach Google Drive. DiaRoutine will try again.")
        } finally {
            mutableStatus.update { it.copy(working = false) }
        }
    }

    override suspend fun findBackup(): CloudBackupInfo? = withContext(Dispatchers.IO) {
        try {
            val file = DriveClient(auth.token(interactive = true)).find() ?: return@withContext null
            val ownKey = prefs.getString(KEY_ID, null)
            CloudBackupInfo(
                savedAt = file.modifiedAt,
                sizeBytes = file.sizeBytes,
                madeWithThisKey = if (ownKey == null || file.keyId == null) null else ownKey == file.keyId,
            )
        } catch (e: DriveAuthException) {
            problem(e.message)
            null
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun restore(recoveryKey: String): RestoreResult = withContext(Dispatchers.IO) {
        val key = RecoveryKey.parse(recoveryKey) ?: return@withContext RestoreResult.WrongKey
        try {
            val drive = DriveClient(auth.token(interactive = true))
            val file = drive.find() ?: return@withContext RestoreResult.NoBackup
            val plain = BackupCrypto.open(drive.download(file.id), key)
            codec.import(plain)
            // Carry on backing up to the same place, with the same key.
            vault.store(key)
            prefs.edit {
                putString(KEY_ID, BackupCrypto.keyId(key))
                putBoolean(CONNECTED, true)
                putLong(LAST_BACKUP, file.modifiedAt.toEpochMilliseconds())
                remove(PROBLEM)
            }
            setWeekly(true)
            RestoreResult.Restored
        } catch (_: BackupCrypto.WrongKeyException) {
            RestoreResult.WrongKey
        } catch (_: BackupCrypto.NotABackupException) {
            RestoreResult.Failed("The file in your Drive isn't a DiaRoutine backup.")
        } catch (_: UnsupportedBackupException) {
            RestoreResult.Failed("This backup was made by a newer version of DiaRoutine. Update the app, then try again.")
        } catch (e: DriveAuthException) {
            RestoreResult.Failed(e.message ?: "Couldn't connect to Google Drive.")
        } catch (_: IOException) {
            RestoreResult.Failed("Couldn't download the backup. Check your connection and try again.")
        }
    }

    override suspend fun deleteBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = DriveClient(auth.token(interactive = true))
            drive.find()?.let { drive.delete(it.id) }
            prefs.edit { remove(LAST_BACKUP) }
            publish()
            true
        } catch (e: DriveAuthException) {
            problem(e.message)
        } catch (_: IOException) {
            problem("Couldn't reach Google Drive to delete the backup.")
        }
    }

    private fun problem(message: String?): Boolean {
        prefs.edit { putString(PROBLEM, message ?: "Something went wrong with backup.") }
        publish()
        return false
    }

    private fun publish() {
        val saved = savedStatus()
        mutableStatus.update { saved.copy(working = it.working) }
    }

    private fun savedStatus() = BackupStatus(
        account = prefs.getString(ACCOUNT, null),
        connected = prefs.getBoolean(CONNECTED, false),
        hasRecoveryKey = prefs.contains(KEY_ID),
        weekly = prefs.getBoolean(WEEKLY, false),
        lastBackupAt = prefs.getLong(LAST_BACKUP, 0).takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds),
        problem = prefs.getString(PROBLEM, null),
    )

    private companion object {
        const val CONNECTED = "connected"
        const val ACCOUNT = "account"
        const val KEY_ID = "key_id"
        const val WEEKLY = "weekly"
        const val LAST_BACKUP = "last_backup"
        const val PROBLEM = "problem"
    }
}
