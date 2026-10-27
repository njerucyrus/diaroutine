package com.diaroutine.screenshots

import androidx.compose.runtime.Composable
import com.diaroutine.domain.WeeklyReport
import com.diaroutine.domain.model.MealContext
import com.diaroutine.features.insights.InsightsScreen
import com.diaroutine.features.meds.MedicationEditorSheet
import com.diaroutine.features.meds.MedsScreen
import com.diaroutine.features.onboarding.OnboardingScreen
import com.diaroutine.features.profile.ProfileScreen
import com.diaroutine.features.today.TodayScreen
import com.diaroutine.features.track.GlucoseLogSheet
import com.diaroutine.features.track.GlucoseSheetRequest
import com.diaroutine.features.track.TrackScreen
import com.diaroutine.screenshots.Samples.medsState
import com.diaroutine.screenshots.Samples.todayState
import com.diaroutine.screenshots.Samples.zone
import kotlinx.datetime.LocalTime
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

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
        tall("05-profile") { ProfileScreen(Samples.profileState) {} }
        save("06-onboarding", renderScreen { OnboardingScreen { _, _, _ -> } })
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
            WeeklyReport.text(Samples.snapshot, WeeklyReport.figures(Samples.snapshot, Samples.now, zone)),
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
