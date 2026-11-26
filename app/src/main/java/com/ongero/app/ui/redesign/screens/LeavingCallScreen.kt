package com.ongero.app.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongero.app.ui.redesign.components.OutlineButton
import com.ongero.app.ui.redesign.components.PrimaryButton
import com.ongero.app.ui.redesign.components.QuietButton
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors

/**
 * Leaving a call.
 *
 * The current dialog makes someone who already feels unsafe read 34 words before they
 * can get out. Here the question is three words, the call stays dimly visible so leaving
 * reads as reversible, and "Leave quietly" exists — not everyone who wants out wants a
 * confrontation.
 */
@Composable
fun LeavingCallScreen(
    elapsed: String,
    onLeaveAndReport: () -> Unit,
    onLeaveQuietly: () -> Unit,
    onStay: () -> Unit,
) {
    val c = ongeroColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper),
    ) {
        // the call behind, dimmed but present
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.18f),
            verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(c.accent),
            )
            Text(elapsed, style = MaterialTheme.typography.displaySmall, color = c.ink)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surface)
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.line),
            )

            Box(Modifier.height(26.dp))

            Text(
                text = "Leave this call?",
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                textAlign = TextAlign.Center,
            )

            Box(Modifier.height(26.dp))

            PrimaryButton(
                text = "Leave and report",
                onClick = onLeaveAndReport,
                container = c.danger,
            )
            Box(Modifier.height(10.dp))
            OutlineButton(text = "Leave quietly", onClick = onLeaveQuietly)
            QuietButton(text = "Stay", onClick = onStay)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LeavingCallScreenPreview() {
    RedesignTheme(night = true) {
        LeavingCallScreen(
            elapsed = "6:12",
            onLeaveAndReport = {},
            onLeaveQuietly = {},
            onStay = {},
        )
    }
}
