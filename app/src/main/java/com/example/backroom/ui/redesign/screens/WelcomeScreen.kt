package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.GuaranteeRow
import com.example.backroom.ui.redesign.components.Hairline
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * Welcome — the whole of arrival.
 *
 * Replaces Splash, Welcome, How It Works and Safety. The three guarantees are the
 * Safety screen's content, stated once, where they are first relevant.
 */
@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    val c = backroomColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, c.accent.copy(alpha = 0.55f), CircleShape),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Say what\nyou can't.",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "A voice. Not a name.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            GuaranteeRow("Your voice is changed") {
                Icon(Icons.Outlined.Lock, null, tint = c.accent, modifier = Modifier.size(17.dp))
            }
            GuaranteeRow("Nothing is recorded") {
                Icon(Icons.Outlined.VisibilityOff, null, tint = c.accent, modifier = Modifier.size(17.dp))
            }
            GuaranteeRow("Leave with one tap") {
                Icon(Icons.Outlined.Close, null, tint = c.accent, modifier = Modifier.size(17.dp))
            }
            Hairline()
        }

        PrimaryButton(text = "Begin", onClick = onBegin)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    RedesignTheme(night = false) { WelcomeScreen(onBegin = {}) }
}
