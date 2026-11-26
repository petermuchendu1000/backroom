package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.QuietButton
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

enum class CallFeeling { HELPED, OKAY, WRONG }

/**
 * After the call — three doors, no scale, no form. Tapping one finishes the screen.
 */
@Composable
fun AfterCallScreen(onAnswer: (CallFeeling) -> Unit, onSkip: () -> Unit) {
    val c = backroomColors
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
            verticalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "How did that feel?",
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
                textAlign = TextAlign.Center,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeelingRow("It helped", Icons.Outlined.SentimentSatisfied, c.calm) {
                    onAnswer(CallFeeling.HELPED)
                }
                FeelingRow("It was okay", Icons.Outlined.SentimentNeutral, c.muted) {
                    onAnswer(CallFeeling.OKAY)
                }
                FeelingRow("Something was wrong", Icons.Outlined.SentimentDissatisfied, c.danger) {
                    onAnswer(CallFeeling.WRONG)
                }
            }
        }

        QuietButton(text = "Skip", onClick = onSkip)
    }
}

@Composable
private fun FeelingRow(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    val c = backroomColors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            Box(Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AfterCallScreenPreview() {
    RedesignTheme(night = false) { AfterCallScreen(onAnswer = {}, onSkip = {}) }
}
