package com.diaroutine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.diaroutine.core.time.startIn
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A bottom sheet with a title and scrolling content. [content] receives a
 * `dismiss` that animates the sheet away before calling [onDismiss].
 */
@Composable
fun DiaSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = DiaTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = colors.background,
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            content(dismiss)
        }
    }
}

@Composable
fun TimePickerDialog(
    initial: LocalTime,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    title: String = "Choose a time",
) {
    val colors = DiaTheme.colors
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = use24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.card,
                    selectorColor = colors.primary,
                    timeSelectorSelectedContainerColor = colors.primarySoft,
                    timeSelectorUnselectedContainerColor = colors.card,
                    periodSelectorSelectedContainerColor = colors.primarySoft,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) { Text("Set time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val colors = DiaTheme.colors
    // The date picker works in UTC midnights.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.startIn(TimeZone.UTC).toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date) else onDismiss()
                },
            ) { Text("Set date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        colors = DatePickerDefaults.colors(containerColor = colors.background),
    ) {
        DatePicker(state = state)
    }
}

/** A yes/no question before something that can't be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = colors.coralStrong) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A one-field dialog for editing short text such as a name. */
@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    validate: (String) -> String? = { null },
) {
    val colors = DiaTheme.colors
    var value by remember { mutableStateOf(initial) }
    val error = validate(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            FormField(label = label, value = value, onValueChange = { value = it }, error = error?.takeIf { value != initial })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()); onDismiss() }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
