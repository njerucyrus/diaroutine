package com.diaroutine.screenshots

import androidx.compose.runtime.Composable
import com.diaroutine.domain.Report
import com.diaroutine.domain.model.MealContext
import com.diaroutine.backup.BackupStatus
import com.diaroutine.backup.CloudBackupInfo
import com.diaroutine.core.time.instantIn
import com.diaroutine.domain.ReportPeriod
import com.diaroutine.features.backup.BackupCard
import com.diaroutine.features.backup.BackupSetupSheet
import com.diaroutine.features.backup.BackupUiState
import com.diaroutine.features.backup.RestoreSheet
import com.diaroutine.features.backup.RestoreStep
import com.diaroutine.features.backup.SetupStep
import com.diaroutine.features.insights.InsightsScreen
import com.diaroutine.features.premium.PaywallSheet
import com.diaroutine.features.meds.MedicationEditorSheet
import com.diaroutine.features.meds.MedsScreen
import com.diaroutine.features.lock.LockScreen
import com.diaroutine.features.onboarding.OnboardingScreen
import com.diaroutine.features.onboarding.OnboardingStep
import com.diaroutine.features.profile.ProfileScreen
import com.diaroutine.features.today.TodayScreen
import com.diaroutine.features.track.GlucoseLogSheet
import com.diaroutine.features.track.GlucoseSheetRequest
import com.diaroutine.features.track.TrackScreen
import com.diaroutine.screenshots.Samples.medsState
import com.diaroutine.screenshots.Samples.todayState
import com.diaroutine.screenshots.Samples.zone
import com.diaroutine.ui.tour.TourController
import kotlinx.datetime.LocalTime
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

/** Renders each screen with a realistic week of data, as a phone would show it. */
class ScreenshotTest {
    private val out = File("build/screenshots").apply {
        listFiles { f -> f.extension == "png" }?.forEach { it.delete() }
        mkdirs()
    }

    @Test
    fun renderScreens() {
        tall("01-today") { TodayScreen(todayState, {}, {}, zone) }
        save("01-today-phone", renderScreen(selectedTab = 0) { TodayScreen(todayState, {}, {}, zone) })
        save("01-today-dark", renderScreen(dark = true, selectedTab = 0) { TodayScreen(todayState, {}, {}, zone) })
        tall("02-meds") { MedsScreen(medsState, {}, zone) }
        tall("03-track") { TrackScreen(Samples.trackState, {}, zone) }
        tall("04-insights") { InsightsScreen(Samples.insightsState, onShare = {}) }
        tall("05-profile") {
            ProfileScreen(
                Samples.profileState.copy(premium = FreeWithTrial),
                onAction = {},
                onStartTour = {},
                backupCard = { BackupCard(BackupUiState(isPremium = false), zone, {}, {}, NoBackupActions) },
            )
        }
        tall("05-profile-premium") {
            val backedUp = BackupStatus(
                account = "lina@gmail.com", connected = true, hasRecoveryKey = true, weekly = true,
                lastBackupAt = Samples.now.instantIn(zone) - 26.hours,
            )
            ProfileScreen(
                Samples.profileState.copy(premium = PremiumActive),
                onAction = {},
                onStartTour = {},
                backupCard = { BackupCard(BackupUiState(status = backedUp, isPremium = true), zone, {}, {}, NoBackupActions) },
            )
        }
        save("11-paywall", renderScreen { InsightsScreen(Samples.insightsState, onShare = {}); PaywallSheet(FakePremiumStore(FreeWithTrial)) {} })
        save(
            "12-backup-key",
            renderScreen {
                InsightsScreen(Samples.insightsState, onShare = {})
                BackupSetupSheet(SetupStep.SaveKey("7K2M-Q9XD-4HNB-T8VW-PZ3C-6RJE-KA5F-M2YG", saved = true), NoBackupActions, zone)
            },
        )
        save(
            "13-restore",
            renderScreen {
                InsightsScreen(Samples.insightsState, onShare = {})
                RestoreSheet(RestoreStep.Found(CloudBackupInfo(Samples.now.instantIn(zone) - 50.hours, 86_000, null)), NoBackupActions, zone)
            },
        )
        val month = Samples.insightsState.copy(
            period = ReportPeriod.Month,
            isPremium = true,
            figures = Report.figures(Samples.snapshot, Samples.now, zone, ReportPeriod.Month),
        )
        save("14-insights-30-days", renderScreen(selectedTab = 3) { InsightsScreen(month, onShare = {}) })
        // First run: every slide, then each step. Notifications off so the reminders step shows.
        val firstRun = FakePlatform(notificationsAllowed = false)
        (0..3).forEach { slide ->
            save("06-onboarding-slide-${slide + 1}", renderScreen(platform = firstRun) { OnboardingScreen({ it(true) }, {}, initialSlide = slide) })
        }
        listOf(OnboardingStep.Setup, OnboardingStep.Lock, OnboardingStep.Reminders).forEach { step ->
            save("06-onboarding-${step.name.lowercase()}", renderScreen(platform = firstRun) { OnboardingScreen({ it(true) }, {}, initialStep = step) })
        }
        save("09-lock", renderScreen { LockScreen(onUnlock = {}) })
        listOf(0, 2, 4, 6).forEach { step ->
            val tour = TourController().apply { start(step) }
            save("10-tour-step-${step + 1}", renderScreen(selectedTab = 0, tour = tour) { TodayScreen(todayState, {}, {}, zone) })
        }
        save(
            "07-sheet-glucose",
            renderScreen {
                TodayScreen(todayState, {}, {}, zone)
                GlucoseLogSheet(GlucoseSheetRequest(Samples.today, LocalTime(13, 10), MealContext.AfterMeal), Samples.profile, Samples.now, zone, {}, {})
            },
        )
        save(
            "08-sheet-medication",
            renderScreen {
                MedsScreen(medsState, {}, zone)
                MedicationEditorSheet(Samples.meds.first(), Samples.today, {}, {})
            },
        )
        File(out, "summary.txt").writeText(
            Report.text(Samples.snapshot, Report.figures(Samples.snapshot, Samples.now, zone)),
        )
    }

    private fun save(name: String, image: BufferedImage) {
        ImageIO.write(image, "png", File(out, "$name.png"))
    }

    /** Renders the whole scroll, then splits it into phone-height pages so each stays legible. */
    private fun tall(name: String, content: @Composable () -> Unit) {
        val image = renderScreen(heightDp = 2600, content = content)
        val pageHeight = (PHONE_HEIGHT_DP * RENDER_DENSITY).toInt()
        val used = usedHeight(image)
        var top = 0
        var index = 1
        while (top < used) {
            save("$name-$index", image.getSubimage(0, top, image.width, minOf(pageHeight, used - top)))
            top += pageHeight
            index++
        }
    }

    /** Height down to the last row that isn't plain background. */
    private fun usedHeight(image: BufferedImage): Int {
        val background = image.getRGB(2, image.height - 2)
        for (y in image.height - 1 downTo 0) {
            for (x in 0 until image.width step 4) {
                if (image.getRGB(x, y) != background) return minOf(image.height, y + 40)
            }
        }
        return image.height
    }
}
