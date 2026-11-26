package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * Splash.
 *
 * The redesign folds the old splash's line, "Say what you can't.", into Welcome, where
 * it is the first thing a new person reads. Repeating it here two seconds earlier would
 * spend the best line in the app on a screen nobody is looking at yet, so this is just
 * the mark.
 */
@Composable
fun SplashScreen() {
    val c = backroomColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
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
        Text(
            text = "Backroom",
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SplashScreenPreview() {
    RedesignTheme(night = false) { SplashScreen() }
}
