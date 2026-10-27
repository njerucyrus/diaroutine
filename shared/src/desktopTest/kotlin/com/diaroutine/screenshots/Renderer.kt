package com.diaroutine.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertChart
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.diaroutine.platform.LockAvailability
import com.diaroutine.platform.PlatformServices
import com.diaroutine.platform.ReminderAccess
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.MaxContentWidth
import com.diaroutine.ui.components.FloatingNavBar
import com.diaroutine.ui.components.NavItem
import com.diaroutine.ui.theme.DarkDiaColors
import com.diaroutine.ui.theme.DiaColors
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.LightDiaColors
import com.diaroutine.ui.tour.LocalTour
import com.diaroutine.ui.tour.TourController
import com.diaroutine.ui.tour.TourOverlay
import com.diaroutine.ui.tour.TourTarget
import com.diaroutine.ui.tour.tourTarget
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

const val PHONE_WIDTH_DP = 392
const val PHONE_HEIGHT_DP = 851
const val RENDER_DENSITY = 2f

private val navItems = listOf(
    NavItem(0, "Today", Icons.Rounded.Home),
    NavItem(1, "Medications", Icons.Rounded.Medication),
    NavItem(2, "Track", Icons.Rounded.MonitorHeart),
    NavItem(3, "Weekly summary", Icons.Rounded.InsertChart),
    NavItem(4, "Profile", Icons.Rounded.Person),
)

/**
 * Draws [content] in the app's theme and returns the image: phone size by default, or
 * any [widthDp] — wider screens get the app's centred column, as on a tablet.
 * With [selectedTab], the floating navigation bar is drawn over it as in the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun renderScreen(
    heightDp: Int = PHONE_HEIGHT_DP,
    widthDp: Int = PHONE_WIDTH_DP,
    dark: Boolean = false,
    colors: DiaColors = if (dark) DarkDiaColors else LightDiaColors,
    selectedTab: Int? = null,
    platform: PlatformServices = FakePlatform(),
    tour: TourController? = null,
    content: @Composable () -> Unit,
): BufferedImage {
    val scene = ImageComposeScene(
        width = (widthDp * RENDER_DENSITY).toInt(),
        height = (heightDp * RENDER_DENSITY).toInt(),
        density = Density(RENDER_DENSITY),
    ) {
        DiaTheme(darkTheme = dark, colors = colors) {
            CompositionLocalProvider(LocalPlatform provides platform, LocalTour provides tour) {
                Box(Modifier.fillMaxSize().background(DiaTheme.colors.background), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = MaxContentWidth).fillMaxHeight()) {
                        content()
                        if (selectedTab != null) {
                            // As in the app's MainShell: content fades out beneath the floating bar.
                            val background = DiaTheme.colors.background
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background.copy(alpha = 0.92f), background))),
                            )
                            FloatingNavBar(
                                navItems, selectedTab, {},
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).tourTarget(tour, TourTarget.NavBar),
                            )
                        }
                        if (tour != null) TourOverlay(tour)
                    }
                }
            }
        }
    }
    // Let animations (gauges, rings, sheets) run to their end.
    var frame = 0L
    repeat(30) {
        scene.render(frame)
        frame += 100_000_000L
    }
    val bytes = scene.render(frame).encodeToData(EncodedImageFormat.PNG)!!.bytes
    scene.close()
    return ImageIO.read(ByteArrayInputStream(bytes))
}

class FakePlatform(
    private val notificationsAllowed: Boolean = true,
    private val lock: LockAvailability = LockAvailability.Available,
) : PlatformServices {
    override val uses24HourClock = false
    override val appVersion = "1.0.0"
    override fun shareText(subject: String, text: String) = Unit
    override fun openUrl(url: String) = Unit
    override fun composeEmail(address: String, subject: String) = Unit
    override fun reminderAccess() = ReminderAccess(notificationsAllowed, false, true)
    override fun requestNotificationAccess() = Unit
    override fun openPreciseTimingSettings() = Unit
    override fun lockAvailability() = lock
    override fun authenticate(onResult: (Boolean) -> Unit) = onResult(true)
    override fun openSecuritySettings() = Unit
}
