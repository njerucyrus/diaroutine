package com.diaroutine.app

import com.diaroutine.core.time.plusDays
import com.diaroutine.core.time.startIn
import com.diaroutine.core.time.ticker
import com.diaroutine.data.AppointmentRepository
import com.diaroutine.data.GlucoseRepository
import com.diaroutine.data.HabitRepository
import com.diaroutine.data.MedicationRepository
import com.diaroutine.data.ProfileRepository
import com.diaroutine.data.db.AppDatabase
import com.diaroutine.domain.ExampleRoutine
import com.diaroutine.domain.ReminderPlanner
import com.diaroutine.domain.RoutineSnapshot
import com.diaroutine.domain.WeeklyReport
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.platform.PlatformServices
import com.diaroutine.platform.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The app's object graph, built once per process by the platform's entry point
 * (Android's Application, later iOS's MainViewController).
 */
class AppContainer(
    database: AppDatabase,
    val platform: PlatformServices,
    private val reminderScheduler: ReminderScheduler,
    val clock: Clock = Clock.System,
) {
    /** Read each time so a change of time zone is picked up. */
    val timeZone: TimeZone get() = TimeZone.currentSystemDefault()

    /** Outlives any screen: reminder sync and work started from notifications. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val profile = ProfileRepository(database.profileDao())
    val medications = MedicationRepository(database.medicationDao(), database.doseLogDao(), clock) { timeZone }
    val glucose = GlucoseRepository(database.glucoseDao())
    val habits = HabitRepository(database.habitDao())
    val appointments = AppointmentRepository(database.appointmentDao())

    val appLock = AppLock(clock)

    init {
        // On the main thread, alongside the platform's shown/hidden calls.
        scope.launch(Dispatchers.Main) {
            profile.profile.map { it.appLock }.distinctUntilChanged().collect(appLock::apply)
        }
    }

    fun now(): LocalDateTime = clock.now().toLocalDateTime(timeZone)

    fun today(): LocalDate = now().date

    /** The routine for the dates [from] through [to], updating as anything in it changes. */
    fun snapshot(from: LocalDate, to: LocalDate): Flow<RoutineSnapshot> {
        val zone = timeZone
        val core = combine(
            profile.profile,
            medications.all,
            medications.doseLogs(from, to),
            glucose.readings(from.startIn(zone), to.plusDays(1).startIn(zone)),
            habits.habits(from, to),
        ) { profile, medications, doseLogs, readings, habits ->
            RoutineSnapshot(profile, medications, doseLogs, readings, habits, appointments = emptyList())
        }
        return combine(core, appointments.all) { snapshot, appointments -> snapshot.copy(appointments = appointments) }
    }

    suspend fun completeOnboarding(name: String, unit: GlucoseUnit, withExampleRoutine: Boolean, appLockEnabled: Boolean) {
        val today = today()
        if (withExampleRoutine) {
            ExampleRoutine.medications(today).forEach { medications.save(it) }
            ExampleRoutine.appointments(today).forEach { appointments.save(it) }
        }
        profile.update {
            it.copy(
                name = name.trim(),
                glucoseUnit = unit,
                appLock = it.appLock.copy(enabled = appLockEnabled),
                onboarded = true,
            )
        }
    }

    suspend fun completeTour() = profile.update { it.copy(tourCompleted = true) }

    /** The past week as text, for the person to send wherever they choose. */
    suspend fun weeklySummaryText(): String {
        val now = now()
        val snapshot = snapshot(now.date.plusDays(-(WeeklyReport.HISTORY_DAYS - 1)), now.date).first()
        return WeeklyReport.text(snapshot, WeeklyReport.figures(snapshot, now, timeZone))
    }

    private val rescheduling = Mutex()

    /** Re-plans the next two days of reminders from the routine as it stands now. */
    suspend fun rescheduleReminders() = rescheduling.withLock {
        val now = clock.now()
        val zone = timeZone
        val today = now.toLocalDateTime(zone).date
        val snapshot = snapshot(today, today.plusDays(2)).first()
        reminderScheduler.replaceAll(ReminderPlanner.plan(now, zone, snapshot, platform.uses24HourClock))
    }

    /** Re-plans reminders in the background. Failures are logged, never thrown. */
    fun rescheduleSoon(): Job = scope.launch { rescheduleSafely() }

    private suspend fun rescheduleSafely() {
        try {
            rescheduleReminders()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("DiaRoutine: rescheduling reminders failed: $e")
        }
    }

    private var syncing = false

    /** Keeps scheduled reminders in step with the routine for as long as the process lives. */
    fun startReminderSync() {
        if (syncing) return
        syncing = true
        scope.launch {
            val today = today()
            merge(
                clock.ticker(30.minutes),
                profile.profile,
                medications.all,
                medications.doseLogs(today, today.plusDays(2)),
                glucose.recent(1),
                habits.habits(today, today),
                appointments.all,
            ).debounce(500.milliseconds).collectLatest { rescheduleSafely() }
        }
    }
}
