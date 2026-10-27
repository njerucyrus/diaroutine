package com.diaroutine.premium

import kotlinx.coroutines.flow.StateFlow

/** What DiaRoutine Premium adds. Restoring a backup is never behind it. */
enum class PremiumFeature(val title: String, val detail: String) {
    CloudBackup(
        "Encrypted Google Drive backup",
        "Automatic weekly backups, encrypted on your phone with a recovery key only you hold.",
    ),
    FollowUps(
        "Missed-dose follow-ups",
        "A reminder again if a dose isn't marked taken, plus a 10-minute snooze on every dose.",
    ),
    LongerInsights(
        "30- and 90-day insights",
        "Longer adherence, glucose and habit trends, to spot patterns over months.",
    ),
}

/** One way to subscribe, as the store offers it to this person. */
data class PremiumPlan(
    /** The store's base plan, e.g. "yearly". */
    val id: String,
    val title: String,
    /** Already formatted by the store in the person's currency, e.g. "KSh 2,900". */
    val price: String,
    /** "month" or "year". */
    val period: String,
    /** "3 months" when this person is eligible for the free trial; null otherwise. */
    val freeTrial: String?,
    /** The store's opaque handle for buying this exact offer. */
    val offerToken: String,
)

enum class PremiumAccess { Unknown, Free, Premium }

data class PremiumState(
    val access: PremiumAccess = PremiumAccess.Unknown,
    val plans: List<PremiumPlan> = emptyList(),
    /** Null while the store is being asked; false when plans couldn't be loaded. */
    val plansAvailable: Boolean? = null,
    /** A purchase is waiting on the store (e.g. a pending payment method). */
    val pending: Boolean = false,
    /** Debug builds only: lets a developer switch Premium on without buying it. */
    val canSimulate: Boolean = false,
) {
    val isPremium: Boolean get() = access == PremiumAccess.Premium
    val trialOffered: Boolean get() = plans.any { it.freeTrial != null }
}

/** The platform's app store: Google Play Billing on Android. */
interface PremiumStore {
    val state: StateFlow<PremiumState>

    /** Re-reads purchases and plans, e.g. "Restore purchases". */
    fun refresh()

    fun purchase(plan: PremiumPlan)

    /** Opens the store's subscription management page. */
    fun manageSubscription()

    /** Only when [PremiumState.canSimulate]. */
    fun simulate(premium: Boolean)
}
