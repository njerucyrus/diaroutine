package com.diaroutine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing

/** The rounded powder-blue card that groups content. */
@Composable
fun DiaCard(
    modifier: Modifier = Modifier,
    color: Color = DiaTheme.colors.card,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(DiaShapes.card)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** An icon in a circle, as in the screenshot's white pill badges. Decorative unless given a description. */
@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = DiaTheme.colors.raised,
    tint: Color = DiaTheme.colors.text,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A round outlined button, 48dp so it's an accessible touch target. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = DiaTheme.colors.raised,
    tint: Color = DiaTheme.colors.text,
    size: Dp = 48.dp,
    badge: Int = 0,
    enabled: Boolean = true,
) {
    val colors = DiaTheme.colors
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(container)
                .border(1.dp, colors.outline.copy(alpha = if (container == colors.raised) 1f else 0f), CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(size * 0.46f))
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(colors.coral)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

enum class PillStyle { Filled, Raised, Soft }

/**
 * A full-width pill action with a trailing arrow bubble — the screenshot's
 * "View all logs ›" button.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    enabled: Boolean = true,
) {
    val colors = DiaTheme.colors
    val (container, content) = when (style) {
        PillStyle.Filled -> colors.primary to colors.onPrimary
        PillStyle.Raised -> colors.raised to colors.text
        PillStyle.Soft -> colors.primarySoft to colors.text
    }
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(DiaShapes.pill)
            .background(if (enabled) container else colors.track)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = if (leadingIcon != null) Spacing.lg else Spacing.xxl, end = if (trailingIcon != null) Spacing.sm else Spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val tint = if (enabled) content else colors.textFaint
        if (leadingIcon != null) Icon(leadingIcon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.weight(1f),
            textAlign = if (trailingIcon != null || leadingIcon != null) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, tint.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(trailingIcon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** "● Scheduled 3" — a label with a count, in a raised pill. */
@Composable
fun CountPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    dot: Color = DiaTheme.colors.primary,
    container: Color = DiaTheme.colors.raised,
) {
    Row(
        modifier = modifier
            .clip(DiaShapes.pill)
            .background(container)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .clearAndSetSemantics { contentDescription = "$label: $count" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelMedium, color = DiaTheme.colors.textMuted)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = DiaTheme.colors.text)
    }
}

/** A small status label: icon plus text, so state is never told by color alone. */
@Composable
fun StatusLabel(
    text: String,
    icon: ImageVector?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(DiaShapes.pill)
            .background(container)
            .padding(start = if (icon != null) 6.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** Two or three pill tabs, the selected one filled — the screenshot's "Meds | Measurement". */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(DiaShapes.pill)
                    .background(if (selected) colors.primarySoft else colors.raised)
                    .border(1.dp, if (selected) colors.primarySoft else colors.outline, DiaShapes.pill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.text else colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }
}

/** A selectable pill for choosing one option in a form. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = DiaTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(DiaShapes.pill)
            .background(if (selected) colors.primary else colors.raised)
            .border(1.dp, if (selected) colors.primary else colors.outline, DiaShapes.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (selected) colors.onPrimary else colors.text
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** The centered title bar with optional round buttons either side — "Med Plus" in the screenshot. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        if (leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DiaTheme.colors.text,
            modifier = Modifier.padding(horizontal = 64.dp).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DiaTheme.colors.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke(this)
    }
}

/** What a section shows when it has nothing yet, with the next step to take. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    DiaCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconBubble(icon, size = 56.dp, tint = DiaTheme.colors.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = DiaTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = DiaTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            PillButton(actionLabel, onAction, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Add, trailingIcon = null)
        }
    }
}

/** − value + with a label; the value is announced as the control's state. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    supporting: String? = null,
) {
    val colors = DiaTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.text)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        CircleIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = "Decrease $label",
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value - step >= range.first,
            size = 44.dp,
        )
        Text(
            text = valueText(value),
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 72.dp)
                .padding(horizontal = Spacing.xs)
                .semantics { stateDescription = valueText(value) },
        )
        CircleIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "Increase $label",
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value + step <= range.last,
            size = 44.dp,
        )
    }
}

/** An outlined text field with a real label (never placeholder-only) and inline error. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    suffix: String? = null,
    supporting: String? = null,
) {
    val colors = DiaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.textFaint) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        suffix = suffix?.let { { Text(it, color = colors.textMuted) } },
        shape = DiaShapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.raised,
            unfocusedContainerColor = colors.raised,
            unfocusedBorderColor = colors.outline,
            focusedBorderColor = colors.primary,
            focusedLabelColor = colors.primaryStrong,
            cursorColor = colors.primary,
        ),
    )
}

/** Switch colours that stay legible on cards when off, not just when on. */
@Composable
fun diaSwitchColors(): SwitchColors {
    val colors = DiaTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onPrimary,
        checkedTrackColor = colors.primary,
        checkedBorderColor = colors.primary,
        uncheckedThumbColor = colors.textMuted,
        uncheckedTrackColor = colors.raised,
        uncheckedBorderColor = colors.textMuted,
    )
}

/** Keyboard options for whole numbers. */
val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

/** Keyboard options for numbers that may have a decimal point. */
val DecimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** A row inside a card: leading icon, title and detail, and an optional trailing slot. */
@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    iconTint: Color = DiaTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconBubble(icon, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = DiaTheme.colors.text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = DiaTheme.colors.textMuted)
        }
        if (trailing != null) trailing()
    }
}

/** A figure with its label, in a white tile on a card. Read out as "label: value". */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = DiaTheme.colors
    Column(
        modifier = modifier
            .clip(DiaShapes.small)
            .background(colors.raised)
            .padding(Spacing.md)
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (icon != null) {
            IconBubble(icon, size = 32.dp, container = colors.card, tint = colors.primaryStrong, modifier = Modifier.padding(bottom = Spacing.xs))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

/** A thin rounded progress bar. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = DiaTheme.colors.primary,
    track: Color = DiaTheme.colors.raised,
    height: Dp = 8.dp,
) {
    Box(modifier.fillMaxWidth().height(height).clip(DiaShapes.pill).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(DiaShapes.pill)
                .background(color),
        )
    }
}

/** Fixed-width spacer for rows. */
@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
