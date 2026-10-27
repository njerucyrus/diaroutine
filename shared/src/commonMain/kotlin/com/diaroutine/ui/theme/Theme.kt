package com.diaroutine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diaroutine.resources.Res
import com.diaroutine.resources.outfit_bold
import com.diaroutine.resources.outfit_medium
import com.diaroutine.resources.outfit_regular
import com.diaroutine.resources.outfit_semibold
import org.jetbrains.compose.resources.Font

/**
 * The app's color roles ("Ocean Teal"). Deep teal on pale aqua cards, with coral
 * kept for the few things that need attention.
 */
@Immutable
data class DiaColors(
    val background: Color,
    /** The pale aqua card that groups content. */
    val card: Color,
    /** A deeper card tone: the navigation bar and pressed tiles. */
    val cardStrong: Color,
    /** White bubbles and chips that sit on a card. */
    val raised: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Primary for text and small marks, dark enough to read on cards. */
    val primaryStrong: Color,
    val primarySoft: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val outline: Color,
    /** The empty part of gauges and progress bars. */
    val track: Color,
    val coral: Color,
    /** Coral for text, readable on coralSoft and cards. */
    val coralStrong: Color,
    val coralSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val sage: Color,
    val sageSoft: Color,
    val isDark: Boolean,
)

val LightDiaColors = DiaColors(
    background = Color(0xFFF2F8F8),
    card = Color(0xFFD9ECEC),
    cardStrong = Color(0xFFC3E0E0),
    raised = Color(0xFFFFFFFF),
    primary = Color(0xFF1E6E74),
    onPrimary = Color(0xFFFFFFFF),
    primaryStrong = Color(0xFF155A5F),
    primarySoft = Color(0xFFB8DCDD),
    text = Color(0xFF14282A),
    textMuted = Color(0xFF465E60),
    textFaint = Color(0xFF77908F),
    outline = Color(0xFFBBD6D6),
    track = Color(0xFFEAF4F4),
    coral = Color(0xFFD9604C),
    coralStrong = Color(0xFFB2432F),
    coralSoft = Color(0xFFFAE1DC),
    amber = Color(0xFF8A5A10),
    amberSoft = Color(0xFFF6E8CF),
    sage = Color(0xFF2F6B4E),
    sageSoft = Color(0xFFD9ECE1),
    isDark = false,
)

val DarkDiaColors = DiaColors(
    background = Color(0xFF0D1617),
    card = Color(0xFF172526),
    cardStrong = Color(0xFF1F3233),
    raised = Color(0xFF233738),
    primary = Color(0xFF74C3C6),
    onPrimary = Color(0xFF082022),
    primaryStrong = Color(0xFF9AD6D8),
    primarySoft = Color(0xFF234A4C),
    text = Color(0xFFE3F0F0),
    textMuted = Color(0xFFA0B9BA),
    textFaint = Color(0xFF77908F),
    outline = Color(0xFF2F4647),
    track = Color(0xFF263B3C),
    coral = Color(0xFFF2907E),
    coralStrong = Color(0xFFF5A595),
    coralSoft = Color(0xFF3B2521),
    amber = Color(0xFFE6B566),
    amberSoft = Color(0xFF3A2F1B),
    sage = Color(0xFF82C4A2),
    sageSoft = Color(0xFF1D3328),
    isDark = true,
)

val LocalDiaColors = staticCompositionLocalOf { LightDiaColors }

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Side margin of every screen. */
    val screen = 20.dp
}

object DiaShapes {
    val card = RoundedCornerShape(28.dp)
    val inner = RoundedCornerShape(20.dp)
    val small = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}

object DiaTheme {
    val colors: DiaColors
        @Composable @ReadOnlyComposable
        get() = LocalDiaColors.current
}

@Composable
fun DiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colors: DiaColors = if (darkTheme) DarkDiaColors else LightDiaColors,
    content: @Composable () -> Unit,
) {
    val typography = diaTypography()
    val colorScheme = remember(colors) { colors.toColorScheme() }
    CompositionLocalProvider(LocalDiaColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = DiaShapes.small,
                medium = DiaShapes.inner,
                large = DiaShapes.card,
                extraLarge = DiaShapes.card,
            ),
            content = content,
        )
    }
}

@Composable
private fun diaTypography(): Typography {
    val outfit = FontFamily(
        Font(Res.font.outfit_regular, FontWeight.Normal),
        Font(Res.font.outfit_medium, FontWeight.Medium),
        Font(Res.font.outfit_semibold, FontWeight.SemiBold),
        Font(Res.font.outfit_bold, FontWeight.Bold),
    )
    return remember(outfit) {
        fun style(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
            fontFamily = outfit,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = tracking.sp,
        )
        Typography(
            displayLarge = style(48, FontWeight.SemiBold, 54, -0.5),
            displayMedium = style(40, FontWeight.SemiBold, 46, -0.4),
            displaySmall = style(32, FontWeight.SemiBold, 38, -0.2),
            headlineLarge = style(30, FontWeight.SemiBold, 36, -0.2),
            headlineMedium = style(26, FontWeight.SemiBold, 32),
            headlineSmall = style(22, FontWeight.SemiBold, 28),
            titleLarge = style(20, FontWeight.SemiBold, 26),
            titleMedium = style(17, FontWeight.Medium, 22),
            titleSmall = style(15, FontWeight.Medium, 20),
            bodyLarge = style(16, FontWeight.Normal, 23),
            bodyMedium = style(14, FontWeight.Normal, 20),
            bodySmall = style(13, FontWeight.Normal, 18),
            labelLarge = style(15, FontWeight.Medium, 20),
            labelMedium = style(13, FontWeight.Medium, 16),
            labelSmall = style(11, FontWeight.Medium, 14, 0.2),
        )
    }
}

private fun DiaColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primarySoft,
        onPrimaryContainer = text,
        secondary = primaryStrong,
        onSecondary = onPrimary,
        secondaryContainer = primarySoft,
        onSecondaryContainer = text,
        tertiary = sage,
        onTertiary = onPrimary,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = card,
        onSurfaceVariant = textMuted,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = raised,
        surfaceContainerLow = background,
        surfaceContainer = card,
        surfaceContainerHigh = if (isDark) cardStrong else raised,
        surfaceContainerHighest = cardStrong,
        outline = outline,
        outlineVariant = outline,
        error = coral,
        onError = if (isDark) onPrimary else Color.White,
        errorContainer = coralSoft,
        onErrorContainer = text,
    )
}
