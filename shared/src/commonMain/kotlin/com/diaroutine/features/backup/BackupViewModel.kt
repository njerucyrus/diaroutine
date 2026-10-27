package com.diaroutine.features.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaroutine.app.AppContainer
import com.diaroutine.backup.BackupStatus
import com.diaroutine.backup.CloudBackupInfo
import com.diaroutine.backup.RestoreResult
import com.diaroutine.platform.LockAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Turning backup on: connect Drive, save the recovery key, make the first backup. */
sealed interface SetupStep {
    data object Connecting : SetupStep

    /** Drive already holds a backup made with another key; setting up anew would replace it. */
    data class ExistingBackup(val info: CloudBackupInfo) : SetupStep
    data class SaveKey(val key: String, val saved: Boolean = false) : SetupStep
    data object BackingUp : SetupStep
    data object Done : SetupStep
    data class Failed(val message: String) : SetupStep
}

/** Bringing a backup back: connect Drive, find the backup, unlock it with the recovery key. */
sealed interface RestoreStep {
    data object Connecting : RestoreStep
    data object NotFound : RestoreStep
    data class Found(val info: CloudBackupInfo, val wrongKey: Boolean = false, val failure: String? = null) : RestoreStep
    data object Restoring : RestoreStep
    data object Restored : RestoreStep
    data class Failed(val message: String) : RestoreStep
}

/** What the backup screens can ask for; the view model does it. */
interface BackupActions {
    fun startSetup()
    fun replaceExistingBackup()
    fun setKeySaved(saved: Boolean)
    fun finishSetup()
    fun closeSetup()
    fun startRestore()
    fun useAnotherAccount()
    fun restore(recoveryKey: String)
    fun closeRestore()
    fun backUpNow()
    fun setWeekly(enabled: Boolean)
    fun revealKey()
    fun hideKey()
    fun copyKey(key: String)
    fun disconnect()
    fun deleteBackup()
}

data class BackupUiState(
    val status: BackupStatus = BackupStatus(),
    val isPremium: Boolean = false,
    val setup: SetupStep? = null,
    val restore: RestoreStep? = null,
    /** The recovery key while the person has asked to see it. */
    val shownKey: String? = null,
)

class BackupViewModel(private val container: AppContainer) : ViewModel(), BackupActions {
    private val backup = container.backup
    private val platform = container.platform
    private val setup = MutableStateFlow<SetupStep?>(null)
    private val restore = MutableStateFlow<RestoreStep?>(null)
    private val shownKey = MutableStateFlow<String?>(null)

    val state: StateFlow<BackupUiState> = combine(backup.status, container.premium.state, setup, restore, shownKey) { status, premium, setup, restore, key ->
        BackupUiState(status, premium.isPremium, setup, restore, key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    override fun startSetup() = launch {
        setup.value = SetupStep.Connecting
        if (!backup.connect()) {
            setup.value = SetupStep.Failed(CONNECT_FAILED)
            return@launch
        }
        val existing = backup.findBackup()
        setup.value = if (existing != null && existing.madeWithThisKey != true) SetupStep.ExistingBackup(existing) else SetupStep.SaveKey(currentOrNewKey())
    }

    /** Chosen at [SetupStep.ExistingBackup]: start fresh, replacing the old backup at the first backup. */
    override fun replaceExistingBackup() = launch {
        setup.value = SetupStep.SaveKey(currentOrNewKey())
    }

    override fun setKeySaved(saved: Boolean) {
        (setup.value as? SetupStep.SaveKey)?.let { setup.value = it.copy(saved = saved) }
    }

    override fun finishSetup() = launch {
        setup.value = SetupStep.BackingUp
        backup.setWeekly(true)
        setup.value = if (backup.backUpNow()) {
            SetupStep.Done
        } else {
            SetupStep.Failed(backup.status.value.problem ?: "The first backup didn't finish. DiaRoutine will try again automatically.")
        }
    }

    override fun closeSetup() {
        setup.value = null
    }

    override fun startRestore() = launch {
        setup.value = null
        restore.value = RestoreStep.Connecting
        if (!backup.connect()) {
            restore.value = RestoreStep.Failed(CONNECT_FAILED)
            return@launch
        }
        restore.value = backup.findBackup()?.let { RestoreStep.Found(it) } ?: RestoreStep.NotFound
    }

    /** Signs out of Drive so the next connection can pick a different Google account. */
    override fun useAnotherAccount() = launch {
        backup.disconnect()
        startRestore()
    }

    override fun restore(recoveryKey: String) = launch {
        val found = restore.value as? RestoreStep.Found ?: return@launch
        restore.value = RestoreStep.Restoring
        restore.value = when (val result = backup.restore(recoveryKey)) {
            RestoreResult.Restored -> RestoreStep.Restored
            RestoreResult.WrongKey -> found.copy(wrongKey = true, failure = null)
            RestoreResult.NoBackup -> RestoreStep.NotFound
            is RestoreResult.Failed -> found.copy(wrongKey = false, failure = result.message)
        }
    }

    override fun closeRestore() {
        restore.value = null
    }

    override fun backUpNow() = launch { backup.backUpNow() }

    override fun setWeekly(enabled: Boolean) = backup.setWeekly(enabled)

    /** Shows the recovery key, after the person verifies when the phone can check. */
    override fun revealKey() {
        val reveal = { launch { shownKey.value = backup.recoveryKey() } }
        if (platform.lockAvailability() == LockAvailability.Available) {
            container.appLock.authenticate(platform::authenticate) { verified -> if (verified) reveal() }
        } else {
            reveal()
        }
    }

    override fun hideKey() {
        shownKey.value = null
    }

    override fun copyKey(key: String) = platform.copyText(key, sensitive = true)

    override fun disconnect() = launch { backup.disconnect() }

    override fun deleteBackup() = launch { backup.deleteBackup() }

    private suspend fun currentOrNewKey(): String = backup.recoveryKey() ?: backup.createRecoveryKey()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val CONNECT_FAILED = "Couldn't connect to Google Drive. Check your connection and try again."
    }
}
