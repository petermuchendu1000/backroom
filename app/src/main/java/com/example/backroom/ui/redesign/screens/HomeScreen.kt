package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.components.Rings
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * Home — one decision.
 *
 * The Sharer/Listener tab pair is gone: it forced an identity choice at launch. Listening
 * is a quiet second door instead, and connection state is a dot rather than a sentence.
 */
@Composable
fun HomeScreen(
    connected: Boolean,
    onStart: () -> Unit,
    onListen: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    val c = backroomColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 44.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (connected) c.calm else c.danger),
                )
                Text(
                    text = if (connected) "Ready" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            Row {
                IconButton(onClick = onHelp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Help and crisis resources",
                        tint = c.muted,
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = c.muted,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Rings(
                sizes = listOf(196.dp, 146.dp, 96.dp),
                colors = listOf(c.accentSofter, c.accentMid, c.accent),
            )
            Text(
                text = "Need to talk?",
                style = MaterialTheme.typography.headlineLarge,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
        }

        PrimaryButton(text = "Start", onClick = onStart)

        TextButton(
            onClick = onListen,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Headphones,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(16.dp),
                )
                Text("Listen instead", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RedesignTheme(night = false) {
        HomeScreen(connected = true, onStart = {}, onListen = {}, onSettings = {}, onHelp = {})
    }
}
