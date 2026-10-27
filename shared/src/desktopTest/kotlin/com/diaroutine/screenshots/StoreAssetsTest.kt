package com.diaroutine.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaroutine.features.backup.BackupSetupSheet
import com.diaroutine.features.backup.SetupStep
import com.diaroutine.features.insights.InsightsScreen
import com.diaroutine.features.lock.LockScreen
import com.diaroutine.features.meds.MedsScreen
import com.diaroutine.features.onboarding.OnboardingScreen
import com.diaroutine.features.today.TodayScreen
import com.diaroutine.features.track.TrackScreen
import com.diaroutine.screenshots.Samples.todayState
import com.diaroutine.screenshots.Samples.zone
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.LightDiaColors
import org.jetbrains.skia.EncodedImageFormat
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Google Play listing graphics: the 512px icon, the 1024×500 feature graphic, and
 * screenshots for phones, 7-inch and 10-inch tablets, plus smaller copies for the
 * website. Written to shared/build/playstore.
 */
@OptIn(ExperimentalComposeUiApi::class)
class StoreAssetsTest {
    private val out = File("build/playstore").apply { deleteRecursively(); mkdirs() }
    private val teal = LightDiaColors.primary
    private val tealDeep = Color(0xFF0F4F54)
    private val aqua = LightDiaColors.card

    /**
     * A screenshot size Play asks for: the [canvasWidth]×[canvasHeight] image, the app drawn
     * at the device's own [screenWidthDp]×[screenHeightDp], shown in a frame [frameWidthDp] wide.
     */
    private enum class Device(
        val folder: String,
        val canvasWidth: Int,
        val canvasHeight: Int,
        val density: Float,
        val screenWidthDp: Int,
        val screenHeightDp: Int,
        val frameWidthDp: Int,
        val cornerDp: Int,
        val bezelDp: Int,
    ) {
        Phone("phone", 1080, 1920, 2.7f, 392, 700, 300, 42, 8),
        Tablet7("tablet-7in", 1200, 1920, 2f, 600, 960, 470, 30, 12),
        Tablet10("tablet-10in", 1600, 2560, 2.5f, 800, 1280, 540, 30, 14),
    }

    private data class Shot(
        val file: String,
        val title: String,
        val subtitle: String,
        val dark: Boolean = false,
        val tab: Int? = null,
        val screen: @Composable () -> Unit,
    )

    private val shots = listOf(
        Shot("01-timeline", "Your whole day\nin one timeline", "Doses, checks, walks and visits.", tab = 0) {
            TodayScreen(todayState, {}, {}, zone)
        },
        Shot("02-medications", "Reminders that match\nyour prescription", "Mark doses taken from the notification.", tab = 1) {
            MedsScreen(Samples.medsState, {}, zone)
        },
        Shot("03-glucose", "Know where your\nnumbers stand", "Target range, trend and time in range.", tab = 2) {
            TrackScreen(Samples.trackState, {}, zone)
        },
        Shot("04-weekly", "See your week\nat a glance", "Doses taken, streaks and trends.", tab = 3) {
            InsightsScreen(Samples.insightsState, onShare = {})
        },
        Shot("05-dark", "Easy on the eyes,\nday and night", "A calm dark theme for evenings.", dark = true, tab = 0) {
            TodayScreen(todayState, {}, {}, zone)
        },
        Shot("06-private", "Private\nby design", "No account, no ads, no tracking.") {
            OnboardingScreen({ it(true) }, {}, initialSlide = 3)
        },
        Shot("07-app-lock", "Locked with your\nfingerprint or face", "Keep your health data private.") {
            LockScreen(onUnlock = {})
        },
        Shot("08-backup", "Backed up, readable\nonly by you", "Encrypted weekly backup to your Drive.") {
            InsightsScreen(Samples.insightsState, onShare = {})
            BackupSetupSheet(SetupStep.SaveKey("7K2M-Q9XD-4HNB-T8VW-PZ3C-6RJE-KA5F-M2YG", saved = true), NoBackupActions, zone)
        },
    )

    @Test
    fun renderStoreAssets() {
        val icon = render(512, 512, 1f) { AppIcon(Modifier.fillMaxSize()) }
        save(icon, "icon-512.png")

        File(out, "web").mkdirs()
        for (device in Device.entries) {
            File(out, "screenshots/${device.folder}").mkdirs()
            for (shot in shots) {
                val screen = renderScreen(
                    heightDp = device.screenHeightDp,
                    widthDp = device.screenWidthDp,
                    dark = shot.dark,
                    selectedTab = shot.tab,
                    content = shot.screen,
                ).toBitmap()
                val image = render(device.canvasWidth, device.canvasHeight, device.density) {
                    StoreScreenshot(shot.title, shot.subtitle, screen, device)
                }
                save(image, "screenshots/${device.folder}/${shot.file}.png")
                if (device == Device.Phone) save(scaled(image, 540, 960), "web/${shot.file}.png")
            }
        }

        val screenHeight = Device.Phone.screenHeightDp
        val today = renderScreen(heightDp = screenHeight) { TodayScreen(todayState, {}, {}, zone) }
        save(render(1024, 500, 2f) { FeatureGraphic(today.toBitmap(), icon.toBitmap()) }, "feature-graphic.png")
        save(scaled(renderScreen(heightDp = screenHeight, selectedTab = 0) { TodayScreen(todayState, {}, {}, zone) }, 560, 1000), "web/hero.png")

        save(scaled(icon, 180, 180), "web/apple-touch-icon.png")
        save(scaled(icon, 64, 64), "web/favicon.png")
    }

    /**
     * The launcher icon, full bleed: Google Play adds the rounded mask itself. It shows the
     * adaptive icon's visible 72-unit centre, so the drop is the size people see on their phone.
     */
    @Composable
    private fun AppIcon(modifier: Modifier) {
        Canvas(modifier.background(aqua)) {
            scale(size.width / 72f, pivot = Offset.Zero) {
                translate(-18f, -18f) { drawGlyph() }
            }
        }
    }

    /** The drop and check, in the same 108-unit geometry as androidApp's ic_launcher_foreground. */
    private fun DrawScope.drawGlyph() {
        val drop = Path().apply {
            moveTo(54f, 28f)
            cubicTo(54f, 28f, 36f, 47f, 36f, 62f)
            arcTo(Rect(36f, 44f, 72f, 80f), 180f, -180f, false)
            cubicTo(72f, 47f, 54f, 28f, 54f, 28f)
            close()
        }
        drawPath(drop, teal)
        val check = Path().apply {
            moveTo(45f, 62.5f)
            lineTo(51.5f, 69f)
            lineTo(63f, 56.5f)
        }
        drawPath(check, Color.White, style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** A caption over the app's real screen in a simple device frame. */
    @Composable
    private fun StoreScreenshot(title: String, subtitle: String, screen: ImageBitmap, device: Device) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(teal, tealDeep)))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 40.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 31.sp, lineHeight = 37.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.86f))
            }
            DeviceFrame(
                screen = screen,
                cornerDp = device.cornerDp,
                bezelDp = device.bezelDp,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp).width(device.frameWidthDp.dp),
            )
        }
    }

    @Composable
    private fun DeviceFrame(screen: ImageBitmap, cornerDp: Int, bezelDp: Int, modifier: Modifier) {
        val outer = RoundedCornerShape(cornerDp.dp)
        Box(
            modifier
                .clip(outer)
                .background(Color(0xFF0B1415))
                .border(2.dp, Color.White.copy(alpha = 0.18f), outer)
                .padding(bezelDp.dp),
        ) {
            Image(
                bitmap = screen,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((cornerDp - bezelDp).dp)),
            )
        }
    }

    /** 1024×500: name and promise on the left, the timeline on the right. */
    @Composable
    private fun FeatureGraphic(screen: ImageBitmap, icon: ImageBitmap) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(teal, tealDeep), start = Offset.Zero, end = Offset(1024f, 500f)))) {
            Row(Modifier.fillMaxSize().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                    Text("DiaRoutine", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your diabetes day,\nin one calm timeline.",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 26.sp),
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                Box(Modifier.width(200.dp).fillMaxHeight()) {
                    DeviceFrame(screen, cornerDp = 42, bezelDp = 8, modifier = Modifier.align(Alignment.TopCenter).offset(y = 28.dp).width(176.dp))
                }
                Spacer(Modifier.width(24.dp))
            }
        }
    }

    private fun render(width: Int, height: Int, density: Float, content: @Composable () -> Unit): BufferedImage {
        val scene = ImageComposeScene(width, height, Density(density)) {
            DiaTheme(darkTheme = false) { content() }
        }
        var frame = 0L
        repeat(10) {
            scene.render(frame)
            frame += 100_000_000L
        }
        val bytes = scene.render(frame).encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun BufferedImage.toBitmap(): ImageBitmap {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(this, "png", it) }.toByteArray()
        return org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    private fun scaled(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return result
    }

    /** Play wants 24-bit PNGs without transparency for screenshots and the feature graphic. */
    private fun save(image: BufferedImage, name: String) {
        val opaque = if (image.colorModel.hasAlpha()) {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also {
                val g = it.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()
            }
        } else image
        ImageIO.write(opaque, "png", File(out, name))
    }
}
