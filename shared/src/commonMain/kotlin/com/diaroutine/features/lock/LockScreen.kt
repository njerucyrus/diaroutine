package com.diaroutine.features.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing

/**
 * Covers the app until the person verifies. Opaque and touch-blocking, so nothing
 * underneath can be seen or used; it asks to verify as soon as it appears.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DiaTheme.colors
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .semantics { paneTitle = "DiaRoutine is locked" }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        IconBubble(Icons.Rounded.Lock, size = 96.dp, container = colors.card, tint = colors.primary)
        Text(
            "DiaRoutine is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Unlock with your fingerprint, face or screen lock to see your routine.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        PillButton(
            text = "Unlock",
            onClick = onUnlock,
            leadingIcon = Icons.Rounded.Fingerprint,
            trailingIcon = null,
            modifier = Modifier.padding(top = Spacing.sm).widthIn(max = 320.dp).fillMaxWidth(),
        )
    }
}
