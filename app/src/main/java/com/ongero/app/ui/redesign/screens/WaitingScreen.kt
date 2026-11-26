package com.ongero.app.ui.redesign.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongero.app.ui.redesign.components.QuietButton
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors

/**
 * Waiting — a pace to breathe with, not a spinner that reads as delay.
 *
 * The line is kept verbatim from the current app: it is the best writing in it.
 */
@Composable
fun WaitingScreen(onCancel: () -> Unit) {
    val c = ongeroColors
    val breath = rememberInfiniteTransition(label = "breath")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 44.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Ring(240.dp, c.accentSofter, breath, delayMillis = 1800)
                Ring(176.dp, c.accentMid, breath, delayMillis = 900)
                Ring(112.dp, c.accentDeep, breath, delayMillis = 0)
            }

            Text(
                text = "Take a breath.\nYou're about to be heard.",
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
        }

        QuietButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun Ring(
    size: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    transition: androidx.compose.animation.core.InfiniteTransition,
    delayMillis: Int,
) {
    val spec = infiniteRepeatable<Float>(
        animation = tween(durationMillis = 7000, delayMillis = delayMillis),
        repeatMode = RepeatMode.Reverse,
    )
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = spec,
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = spec,
        label = "alpha",
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WaitingScreenPreview() {
    RedesignTheme(night = false) { WaitingScreen(onCancel = {}) }
}
