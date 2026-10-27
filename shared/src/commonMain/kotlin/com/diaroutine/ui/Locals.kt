package com.diaroutine.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.diaroutine.platform.PlatformServices
import com.diaroutine.ui.theme.Spacing

val LocalPlatform = staticCompositionLocalOf<PlatformServices> { error("PlatformServices not provided") }

/** On tablets the app is a centred column no wider than this, rather than stretched edge to edge. */
val MaxContentWidth = 720.dp

/** Height the floating navigation bar takes, including its margin. */
val NavBarClearance = 88.dp

/**
 * Padding for a screen's scrolling content: clear of the status bar at the top
 * and of the floating navigation bar at the bottom. Without [horizontal], each
 * item pads itself, so rows like the date strip can scroll edge to edge.
 */
@Composable
fun screenPadding(horizontal: Boolean = true): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val side = if (horizontal) Spacing.screen else 0.dp
    return PaddingValues(
        start = side,
        end = side,
        top = top + Spacing.sm,
        bottom = bottom + NavBarClearance + Spacing.lg,
    )
}
