package com.diaroutine.features.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.diaroutine.backup.CloudBackupInfo
import com.diaroutine.backup.RecoveryKey
import com.diaroutine.core.format.Fmt
import com.diaroutine.ui.LocalPlatform
import com.diaroutine.ui.components.ConfirmDialog
import com.diaroutine.ui.components.DiaCard
import com.diaroutine.ui.components.DiaSheet
import com.diaroutine.ui.components.FormField
import com.diaroutine.ui.components.IconBubble
import com.diaroutine.ui.components.InfoRow
import com.diaroutine.ui.components.PillButton
import com.diaroutine.ui.components.PillStyle
import com.diaroutine.ui.components.StatusLabel
import com.diaroutine.ui.components.diaSwitchColors
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme
import com.diaroutine.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The Profile card: backup status and everything you can do with it. */
@Composable
fun BackupCard(
    state: BackupUiState,
    zone: TimeZone,
    onSetUp: () -> Unit,
    onOfferPremium: () -> Unit,
    actions: BackupActions,
) {
    val colors = DiaTheme.colors
    val status = state.status
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    DiaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconBubble(if (status.lastBackupAt != null) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync, tint = colors.primaryStrong)
            Column(Modifier.weight(1f)) {
                Text("Backup & restore", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(
                    "Encrypted on your phone, kept in your Google Drive",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            if (!state.isPremium) StatusLabel("Premium", Icons.Rounded.Lock, colors.primarySoft, colors.primaryStrong)
        }

        when {
            status.connected && status.hasRecoveryKey -> {
                InfoRow(
                    icon = Icons.Rounded.CloudDone,
                    title = status.lastBackupAt?.let { "Last backup ${savedAt(it, zone)}" } ?: "No backup yet",
                    detail = status.account ?: "Google Drive",
                    iconTint = colors.sage,
                )
                status.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.coralStrong) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Back up every week", style = MaterialTheme.typography.titleSmall, color = colors.text)
                        Text(
                            if (state.isPremium) "Automatically, on Wi-Fi or mobile data" else "Needs Premium",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    Switch(
                        checked = status.weekly && state.isPremium,
                        onCheckedChange = { if (state.isPremium) actions.setWeekly(it) else onOfferPremium() },
                        colors = diaSwitchColors(),
                    )
                }
                PillButton(
                    text = if (status.working) "Backing up…" else "Back up now",
                    onClick = { if (state.isPremium) actions.backUpNow() else onOfferPremium() },
                    enabled = !status.working,
                    leadingIcon = Icons.Rounded.CloudSync,
                    trailingIcon = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = actions::revealKey) { Text("Show recovery key", color = colors.primaryStrong) }
                    TextButton(onClick = actions::startRestore) { Text("Restore", color = colors.primaryStrong) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { confirmDisconnect = true }) { Text("Turn off", color = colors.textMuted) }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete backup", color = colors.coralStrong) }
                }
            }
            else -> {
                Text(
                    "Keep a copy of your routine and history in your own Google Drive. It's encrypted on this phone first, " +
                        "with a recovery key only you hold — no one else can read it, not even us.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                PillButton(
                    text = if (state.isPremium) "Set up backup" else "Try Premium to back up",
                    onClick = { if (state.isPremium) onSetUp() else onOfferPremium() },
                    leadingIcon = Icons.Rounded.CloudSync,
                    trailingIcon = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = actions::startRestore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Restore from a backup", color = colors.primaryStrong)
                }
            }
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Turn off backup?",
            message = "Backups stop and this phone forgets your Google account and recovery key. Your last backup stays in Drive " +
                "and can still be restored with your recovery key.",
            confirmLabel = "Turn off",
            onConfirm = actions::disconnect,
            onDismiss = { confirmDisconnect = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete your backup?",
            message = "The backup is removed from your Google Drive and can't be restored. Data on this phone isn't affected.",
            confirmLabel = "Delete backup",
            onConfirm = actions::deleteBackup,
            onDismiss = { confirmDelete = false },
        )
    }
    state.shownKey?.let { key ->
        RecoveryKeyDialog(key, onCopy = { actions.copyKey(key) }, onDismiss = actions::hideKey)
    }
}

@Composable
fun BackupSetupSheet(step: SetupStep, actions: BackupActions, zone: TimeZone) {
    val colors = DiaTheme.colors
    DiaSheet(title = "Set up backup", onDismiss = actions::closeSetup) { dismiss ->
        when (step) {
            SetupStep.Connecting -> Working("Connecting to Google Drive…")
            is SetupStep.ExistingBackup -> {
                InfoRow(Icons.Rounded.CloudDone, "A backup is already in your Drive", detail = "Saved ${savedAt(step.info.savedAt, zone)}")
                Text(
                    "It was made with a different recovery key. If you continue, your next backup replaces it. " +
                        "If you need the data in it, restore it first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                PillButton("Restore it instead", actions::startRestore, Modifier.fillMaxWidth())
                PillButton("Replace it", actions::replaceExistingBackup, Modifier.fillMaxWidth(), style = PillStyle.Raised)
            }
            is SetupStep.SaveKey -> {
                Text(
                    "This is your recovery key. You'll need it to restore your backup on a new phone. " +
                        "Save it somewhere safe — a password manager is ideal. Without it, the backup can't be opened by anyone, including us.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                RecoveryKeyText(step.key)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PillButton(
                        text = "Copy",
                        onClick = { actions.copyKey(step.key) },
                        style = PillStyle.Raised,
                        leadingIcon = Icons.Rounded.ContentCopy,
                        trailingIcon = null,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(DiaShapes.small)
                        .toggleable(value = step.saved, role = Role.Checkbox, onValueChange = actions::setKeySaved)
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = step.saved, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.primary))
                    Text("I've saved my recovery key somewhere safe", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                }
                PillButton("Turn on weekly backup", actions::finishSetup, Modifier.fillMaxWidth(), enabled = step.saved)
            }
            SetupStep.BackingUp -> Working("Encrypting and backing up…")
            SetupStep.Done -> {
                Outcome(Icons.Rounded.CheckCircle, colors.sage, "Backup is on", "Your data is backed up and will be again every week.")
                PillButton("Done", dismiss, Modifier.fillMaxWidth(), trailingIcon = null)
            }
            is SetupStep.Failed -> {
                Outcome(Icons.Rounded.ErrorOutline, colors.coralStrong, "Something went wrong", step.message)
                PillButton("Try again", actions::startSetup, Modifier.fillMaxWidth(), trailingIcon = null)
            }
        }
    }
}

@Composable
fun RestoreSheet(step: RestoreStep, actions: BackupActions, zone: TimeZone) {
    val colors = DiaTheme.colors
    DiaSheet(title = "Restore from backup", onDismiss = actions::closeRestore) { dismiss ->
        when (step) {
            RestoreStep.Connecting -> Working("Looking in your Google Drive…")
            RestoreStep.NotFound -> {
                Outcome(Icons.Rounded.CloudSync, colors.textMuted, "No backup found", "There's no DiaRoutine backup in this Google account.")
                PillButton("Use another Google account", actions::useAnotherAccount, Modifier.fillMaxWidth(), style = PillStyle.Raised, trailingIcon = null)
            }
            is RestoreStep.Found -> RestoreForm(step, actions, zone)
            RestoreStep.Restoring -> Working("Decrypting and restoring…")
            RestoreStep.Restored -> {
                Outcome(Icons.Rounded.CheckCircle, colors.sage, "Your data is back", "Your routine and history have been restored on this phone.")
                PillButton("Done", dismiss, Modifier.fillMaxWidth(), trailingIcon = null)
            }
            is RestoreStep.Failed -> {
                Outcome(Icons.Rounded.ErrorOutline, colors.coralStrong, "Something went wrong", step.message)
                PillButton("Try again", actions::startRestore, Modifier.fillMaxWidth(), trailingIcon = null)
            }
        }
    }
}

@Composable
private fun ColumnScope.RestoreForm(step: RestoreStep.Found, actions: BackupActions, zone: TimeZone) {
    val colors = DiaTheme.colors
    var key by remember { mutableStateOf("") }
    val valid = RecoveryKey.parse(key) != null
    InfoRow(
        Icons.Rounded.CloudDone,
        "Backup from ${savedAt(step.info.savedAt, zone)}",
        detail = "${(step.info.sizeBytes / 1024).coerceAtLeast(1)} KB, encrypted",
        iconTint = colors.sage,
    )
    FormField(
        label = "Recovery key",
        value = key,
        onValueChange = { key = it.take(48) },
        placeholder = "XXXX-XXXX-XXXX-XXXX-…",
        error = when {
            step.wrongKey -> "That recovery key doesn't open this backup. Check it and try again."
            key.isNotBlank() && !valid -> "A recovery key is 32 letters and numbers"
            else -> null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    step.failure?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.coralStrong) }
    Text(
        "Restoring replaces everything on this phone with the backup.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.amber,
    )
    PillButton(
        text = "Restore",
        onClick = { actions.restore(key) },
        enabled = valid,
        leadingIcon = Icons.Rounded.Restore,
        trailingIcon = null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The recovery key in large monospaced groups, read out group by group. */
@Composable
fun RecoveryKeyText(key: String) {
    val colors = DiaTheme.colors
    val groups = key.split('-')
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DiaShapes.inner)
            .background(colors.raised)
            .padding(Spacing.lg)
            .semantics { contentDescription = "Recovery key: " + groups.joinToString(", ") { it.toCharArray().joinToString(" ") } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        groups.chunked(4).forEach { row ->
            Text(
                row.joinToString("  "),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                letterSpacing = 1.sp,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun RecoveryKeyDialog(key: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val colors = DiaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        icon = { Icon(Icons.Rounded.Key, null, tint = colors.primary) },
        title = { Text("Your recovery key", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RecoveryKeyText(key)
                Text(
                    "Anyone with this key and access to your Google account could read your backup. Keep it private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onCopy) { Text("Copy") } },
    )
}

@Composable
private fun Working(message: String) {
    val colors = DiaTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(28.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.text)
    }
}

@Composable
private fun Outcome(icon: ImageVector, tint: Color, title: String, body: String) {
    val colors = DiaTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        IconBubble(icon, size = 72.dp, container = colors.card, tint = tint)
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** "Tue, 27 Oct at 9:41 PM" */
@Composable
private fun savedAt(instant: Instant, zone: TimeZone): String {
    val use24Hour = LocalPlatform.current.uses24HourClock
    val at = instant.toLocalDateTime(zone)
    return "${Fmt.dateMedium(at.date)} at ${Fmt.time(at.time, use24Hour)}"
}
