package com.ongero.app.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongero.app.ui.redesign.components.OutlineButton
import com.ongero.app.ui.redesign.components.PrimaryButton
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors

/**
 * Someone needs to talk — the listener's decision.
 *
 * Their own words are the largest thing here, because that is what the decision is
 * actually made on; topic, tone and length are a single supporting line underneath.
 *
 * The countdown is a thin bar rather than a number counting down. A listener deciding
 * whether they can hold someone's weight does not need a stopwatch in their face, and
 * "$remainingSeconds seconds" in the original turned a human choice into a timed test.
 */
@Composable
fun IncomingPreviewSheet(
    theirWords: String,
    topic: String,
    tone: String,
    minutes: Int,
    secondsLeft: Int,
    totalSeconds: Int,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = ongeroColors
    val progress = if (totalSeconds > 0) {
        (secondsLeft.toFloat() / totalSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 44.dp),
    ) {
        Text(
            text = "Someone needs to talk",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = "“$theirWords”",
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
            )
            Text(
                text = "$topic · $tone · about $minutes min",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
        }

        // Time left, shown as something draining rather than a number ticking down.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent),
            )
        }

        Box(Modifier.height(24.dp))

        PrimaryButton(text = "Accept", onClick = onAccept)
        Box(Modifier.height(10.dp))
        OutlineButton(text = "Not this one", onClick = onSkip)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun IncomingPreviewSheetPreview() {
    RedesignTheme(night = false) {
        IncomingPreviewSheet(
            theirWords = "Lost someone close, need to talk through the pain",
            topic = "Grief",
            tone = "Heavy",
            minutes = 15,
            secondsLeft = 21,
            totalSeconds = 30,
            onAccept = {},
            onSkip = {},
        )
    }
}
