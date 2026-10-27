package com.diaroutine.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.diaroutine.platform.PlatformServices
import com.diaroutine.platform.ReminderAccess
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.FloatingNavBar
import com.diaroutine.ui.components.NavItem
import com.diaroutine.ui.theme.DarkDiaColors
import com.diaroutine.ui.theme.DiaColors
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.LightDiaColors
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
 * Draws [content] at phone width in the app's theme and returns the image.
 * With [selectedTab], the floating navigation bar is drawn over it as in the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun renderScreen(
    heightDp: Int = PHONE_HEIGHT_DP,
    dark: Boolean = false,
    colors: DiaColors = if (dark) DarkDiaColors else LightDiaColors,
    selectedTab: Int? = null,
    content: @Composable () -> Unit,
): BufferedImage {
    val scene = ImageComposeScene(
        width = (PHONE_WIDTH_DP * RENDER_DENSITY).toInt(),
        height = (heightDp * RENDER_DENSITY).toInt(),
        density = Density(RENDER_DENSITY),
    ) {
        DiaTheme(darkTheme = dark, colors = colors) {
            CompositionLocalProvider(LocalPlatform provides FakePlatform) {
                Box(Modifier.fillMaxSize().background(DiaTheme.colors.background)) {
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
                        FloatingNavBar(navItems, selectedTab, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
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

private object FakePlatform : PlatformServices {
    override val uses24HourClock = false
    override val appVersion = "0.1.0"
    override fun shareText(subject: String, text: String) = Unit
    override fun openUrl(url: String) = Unit
    override fun composeEmail(address: String, subject: String) = Unit
    override fun reminderAccess() = ReminderAccess(true, false, true)
    override fun requestNotificationAccess() = Unit
    override fun openPreciseTimingSettings() = Unit
}
