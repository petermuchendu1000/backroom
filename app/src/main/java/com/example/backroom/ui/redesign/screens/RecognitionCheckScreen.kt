package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors
import com.example.backroom.ui.screens.feedback.RecognitionResponse

/**
 * Did you recognise them?
 *
 * This is the one question that tells the team whether voice anonymisation actually
 * works in the field, so the answer must feel costless to give: three taps, no scale,
 * and "Prefer not to say" carries the same weight as the other two.
 *
 * NOTE: this screen is currently unreachable in both designs. The route exists in
 * BackroomNavGraph, but PostCallFeedback navigates straight home instead of here. It is
 * redesigned so it matches if it is ever wired up; wiring it is a product decision, not
 * a styling one, so it is left as it is.
 */
@Composable
fun RecognitionCheckScreen(onAnswer: (RecognitionResponse) -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Did you know their voice?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "This stays between us.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AnswerRow("Yes", RecognitionResponse.YES, onAnswer)
                AnswerRow("No", RecognitionResponse.NO, onAnswer)
                AnswerRow("Rather not say", RecognitionResponse.PREFER_NOT_TO_SAY, onAnswer)
            }
        }
    }
}

@Composable
private fun AnswerRow(
    label: String,
    response: RecognitionResponse,
    onAnswer: (RecognitionResponse) -> Unit,
) {
    val c = backroomColors
    Surface(
        onClick = { onAnswer(response) },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RecognitionCheckScreenPreview() {
    RedesignTheme(night = false) { RecognitionCheckScreen(onAnswer = {}) }
}
