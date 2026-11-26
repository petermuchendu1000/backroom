package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.GroupLabel
import com.example.backroom.ui.redesign.components.Hairline
import com.example.backroom.ui.redesign.components.ScreenHeader
import com.example.backroom.ui.redesign.components.SettingRow
import com.example.backroom.ui.redesign.components.ToggleRow
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * Settings.
 *
 * Two changes beyond styling.
 *
 * The "Voice Anonymization — Basic (mandatory)" row is gone. It was not a setting: it
 * could not be changed and tapping it only raised a toast saying so. What someone is
 * actually looking for when they open Settings and tap it is reassurance, so the
 * reassurance is stated plainly at the top instead of disguised as a control.
 *
 * Subtitles that only restated their title are gone too — "Download your data" under
 * "Export my data", "Permanently delete all data" under "Delete account". Subtitles
 * survive only where they carry state: how many people are blocked, whether Plus is
 * active.
 */
@Composable
fun SettingsScreen(
    blockedCount: Int,
    previewsEnabled: Boolean,
    tipsEnabled: Boolean,
    subscriptionStatus: String?,
    versionName: String,
    onBack: () -> Unit,
    onBoundaries: () -> Unit,
    onBlockedUsers: () -> Unit,
    onSubscription: () -> Unit,
    onPreviewsChanged: (Boolean) -> Unit,
    onTipsChanged: (Boolean) -> Unit,
    onExportData: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = backroomColors
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Box(Modifier.height(24.dp))

        // The promise, stated where someone comes looking for it.
        Text(
            text = "Your voice is always changed, and nothing is recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )

        GroupLabel("Listening")
        Hairline()
        SettingRow(title = "My boundaries", onClick = onBoundaries)
        Hairline()
        SettingRow(
            title = "Blocked people",
            value = if (blockedCount == 0) null else blockedCount.toString(),
            onClick = onBlockedUsers,
        )
        Hairline()

        GroupLabel("Notifications")
        Hairline()
        ToggleRow(
            title = "Someone needs to talk",
            checked = previewsEnabled,
            onCheckedChange = onPreviewsChanged,
        )
        Hairline()
        ToggleRow(title = "Tips", checked = tipsEnabled, onCheckedChange = onTipsChanged)
        Hairline()

        GroupLabel("Plus")
        Hairline()
        SettingRow(
            title = "Backroom Plus",
            value = subscriptionStatus,
            onClick = onSubscription,
        )
        Hairline()

        GroupLabel("Your data")
        Hairline()
        SettingRow(title = "Export my data", onClick = { confirmExport = true })
        Hairline()
        SettingRow(
            title = "Delete account",
            onClick = { confirmDelete = true },
            destructive = true,
        )
        Hairline()

        Box(Modifier.height(32.dp))
        Text(
            text = versionName,
            style = MaterialTheme.typography.labelSmall,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmExport) {
        Confirm(
            title = "Export your data?",
            confirmLabel = "Export",
            onConfirm = { confirmExport = false; onExportData() },
            onDismiss = { confirmExport = false },
        )
    }

    if (confirmDelete) {
        Confirm(
            title = "Delete your account?",
            // The one place a sentence earns its space: this cannot be undone.
            body = "Everything is erased. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; onDeleteAccount() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Confirm(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    destructive: Boolean = false,
) {
    val c = backroomColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
        },
        text = body?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) c.danger else c.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        },
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    RedesignTheme(night = false) {
        SettingsScreen(
            blockedCount = 2,
            previewsEnabled = true,
            tipsEnabled = false,
            subscriptionStatus = null,
            versionName = "Version 1.0.0",
            onBack = {},
            onBoundaries = {},
            onBlockedUsers = {},
            onSubscription = {},
            onPreviewsChanged = {},
            onTipsChanged = {},
            onExportData = {},
            onDeleteAccount = {},
        )
    }
}
