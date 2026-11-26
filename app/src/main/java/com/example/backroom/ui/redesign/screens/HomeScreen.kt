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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.OutlineButton
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.components.Rings
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

enum class HomeMode { TALK, LISTEN }

/**
 * Home — still one decision, now with two sides to it.
 *
 * The original pair of tabs forced an identity choice at launch and left the screen
 * showing two things at once. Here the screen shows one mode at a time and the other is
 * a quiet line at the bottom, so neither side is a form.
 *
 * Availability is the state of the screen rather than a switch on a card: when someone
 * is available the rings breathe and the status line says so, which is also visible from
 * the talking side, so nobody is left wondering whether they are still on call.
 */
@Composable
fun HomeScreen(
    connected: Boolean,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    mode: HomeMode = HomeMode.TALK,
    available: Boolean = false,
    onSwitchMode: (HomeMode) -> Unit = {},
    onSetAvailable: (Boolean) -> Unit = {},
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
                        .background(
                            when {
                                available -> c.calm
                                connected -> c.calm
                                else -> c.danger
                            },
                        ),
                )
                Text(
                    text = when {
                        available -> "Listening"
                        connected -> "Ready"
                        else -> "Offline"
                    },
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
                colors = if (mode == HomeMode.LISTEN && !available) {
                    listOf(c.line, c.surface, c.muted)
                } else {
                    listOf(c.accentSofter, c.accentMid, c.accent)
                },
                pulse = available,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when {
                        mode == HomeMode.TALK -> "Need to talk?"
                        available -> "Listening"
                        else -> "Ready to listen?"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                if (mode == HomeMode.LISTEN && available) {
                    Text(
                        text = "We'll let you know.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        when {
            mode == HomeMode.TALK -> {
                PrimaryButton(text = "Start", onClick = onStart)
                SwitchModeButton(
                    text = "Listen instead",
                    icon = Icons.Outlined.Headphones,
                    onClick = { onSwitchMode(HomeMode.LISTEN) },
                )
            }

            available -> {
                // Stopping is the only action, so it does not need to shout.
                OutlineButton(text = "Stop listening", onClick = { onSetAvailable(false) })
                SwitchModeButton(
                    text = "Talk instead",
                    icon = Icons.Outlined.Mic,
                    onClick = { onSwitchMode(HomeMode.TALK) },
                )
            }

            else -> {
                PrimaryButton(text = "Go available", onClick = { onSetAvailable(true) })
                SwitchModeButton(
                    text = "Talk instead",
                    icon = Icons.Outlined.Mic,
                    onClick = { onSwitchMode(HomeMode.TALK) },
                )
            }
        }
    }
}

@Composable
private fun SwitchModeButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val c = backroomColors
    TextButton(
        onClick = onClick,
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
                icon,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(16.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeTalkPreview() {
    RedesignTheme(night = false) {
        HomeScreen(connected = true, onStart = {}, onSettings = {}, onHelp = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeListenOffPreview() {
    RedesignTheme(night = false) {
        HomeScreen(
            connected = true,
            onStart = {},
            onSettings = {},
            onHelp = {},
            mode = HomeMode.LISTEN,
            available = false,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeListenOnPreview() {
    RedesignTheme(night = false) {
        HomeScreen(
            connected = true,
            onStart = {},
            onSettings = {},
            onHelp = {},
            mode = HomeMode.LISTEN,
            available = true,
        )
    }
}
