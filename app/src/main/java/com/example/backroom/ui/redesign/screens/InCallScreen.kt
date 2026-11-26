package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.Rings
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * In call — one surface, one promise.
 *
 * "Voice masked" is stated once. The current screen carries four separate
 * reassurances; repeating safety reads as anxiety rather than calm.
 */
@Composable
fun InCallScreen(
    topic: String,
    elapsed: String,
    onMute: () -> Unit,
    onEnd: () -> Unit,
    onFeelUnsafe: () -> Unit,
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "  Voice masked",
                style = MaterialTheme.typography.labelSmall,
                color = c.muted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Rings(
                sizes = listOf(230.dp, 168.dp, 108.dp),
                colors = listOf(c.accentSofter, c.accentMid, c.accent),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(topic, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.displaySmall,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallControl(
                size = 62.dp,
                background = c.surface,
                border = c.line,
                label = "Mute your microphone",
                onClick = onMute,
            ) {
                Icon(Icons.Outlined.Mic, null, tint = c.ink, modifier = Modifier.size(23.dp))
            }

            CallControl(
                size = 82.dp,
                background = c.danger,
                border = c.danger,
                label = "End call",
                onClick = onEnd,
            ) {
                Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }

            CallControl(
                size = 62.dp,
                background = c.surface,
                border = c.line,
                label = "I feel uncomfortable",
                onClick = onFeelUnsafe,
            ) {
                Icon(Icons.Outlined.Shield, null, tint = c.ink, modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun CallControl(
    size: Dp,
    background: Color,
    border: Color,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun InCallScreenPreview() {
    RedesignTheme(night = true) {
        InCallScreen(
            topic = "Letting out",
            elapsed = "6:12",
            onMute = {},
            onEnd = {},
            onFeelUnsafe = {},
        )
    }
}
