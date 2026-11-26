package com.ongero.app.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
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
import com.ongero.app.ui.redesign.components.PrimaryButton
import com.ongero.app.ui.redesign.components.QuietButton
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors

/**
 * Microphone — the only permission asked before the first call.
 *
 * Notifications moves to the moment someone turns on listening; location was never
 * wired into the onboarding chain at all.
 */
@Composable
fun MicPermissionScreen(onAllow: () -> Unit, onSkip: () -> Unit) {
    val c = ongeroColors
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
            verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "We'll need your voice",
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "It's changed before anyone hears it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(270.dp),
                )
            }
        }

        PrimaryButton(text = "Allow microphone", onClick = onAllow)
        QuietButton(text = "Not now", onClick = onSkip)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MicPermissionScreenPreview() {
    RedesignTheme(night = false) { MicPermissionScreen(onAllow = {}, onSkip = {}) }
}
