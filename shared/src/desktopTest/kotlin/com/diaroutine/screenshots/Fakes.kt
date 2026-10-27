package com.diaroutine.screenshots

import com.diaroutine.features.backup.BackupActions
import com.diaroutine.premium.PremiumAccess
import com.diaroutine.premium.PremiumPlan
import com.diaroutine.premium.PremiumState
import com.diaroutine.premium.PremiumStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val SamplePlans = listOf(
    PremiumPlan("monthly", "Monthly", "KSh 350", "month", freeTrial = "3 months", offerToken = "m"),
    PremiumPlan("yearly", "Yearly", "KSh 2,900", "year", freeTrial = "3 months", offerToken = "y"),
)

val FreeWithTrial = PremiumState(access = PremiumAccess.Free, plans = SamplePlans, plansAvailable = true)
val PremiumActive = PremiumState(access = PremiumAccess.Premium, plans = SamplePlans, plansAvailable = true)

class FakePremiumStore(state: PremiumState) : PremiumStore {
    override val state: StateFlow<PremiumState> = MutableStateFlow(state)
    override fun refresh() = Unit
    override fun purchase(plan: PremiumPlan) = Unit
    override fun manageSubscription() = Unit
    override fun simulate(premium: Boolean) = Unit
}

object NoBackupActions : BackupActions {
    override fun startSetup() = Unit
    override fun replaceExistingBackup() = Unit
    override fun setKeySaved(saved: Boolean) = Unit
    override fun finishSetup() = Unit
    override fun closeSetup() = Unit
    override fun startRestore() = Unit
    override fun useAnotherAccount() = Unit
    override fun restore(recoveryKey: String) = Unit
    override fun closeRestore() = Unit
    override fun backUpNow() = Unit
    override fun setWeekly(enabled: Boolean) = Unit
    override fun revealKey() = Unit
    override fun hideKey() = Unit
    override fun copyKey(key: String) = Unit
    override fun disconnect() = Unit
    override fun deleteBackup() = Unit
}
