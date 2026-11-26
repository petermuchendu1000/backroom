package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.GroupLabel
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.components.ScreenHeader
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors
import com.example.backroom.ui.screens.listener.MaxIntensity
import com.example.backroom.ui.screens.sharer.Topic
import kotlin.math.roundToInt

/**
 * My boundaries — what a listener is willing to hold.
 *
 * Uses the ORIGINAL [Topic] and [MaxIntensity] enums rather than the redesign's softer
 * labels, because these values are persisted by name and matched against incoming
 * shares. Changing the display copy here would silently change what a listener is
 * matched with, which is the same trap the sharer wire values had.
 *
 * Topics are chips rather than a checkbox list: this is a set, and a set reads faster
 * as a group of pills than as six rows of ticked boxes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoundariesScreen(
    initialTopics: Set<Topic>,
    initialIntensity: MaxIntensity,
    initialMaxMinutes: Int,
    onBack: () -> Unit,
    onSave: (topics: Set<Topic>, intensity: MaxIntensity, maxMinutes: Int) -> Unit,
) {
    val c = backroomColors
    var topics by remember { mutableStateOf(initialTopics) }
    var intensity by remember { mutableStateOf(initialIntensity) }
    var minutes by remember { mutableFloatStateOf(initialMaxMinutes.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "My boundaries", onBack = onBack)

        GroupLabel("What I'll hear")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Topic.entries.forEach { t ->
                val on = t in topics
                Surface(
                    onClick = { topics = if (on) topics - t else topics + t },
                    shape = RoundedCornerShape(22.dp),
                    color = if (on) c.accent else c.surface,
                    border = BorderStroke(1.dp, if (on) c.accent else c.line),
                ) {
                    Text(
                        text = t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (on) c.onAccent else c.ink,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
                    )
                }
            }
        }

        GroupLabel("How heavy I can go")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaxIntensity.entries.forEach { level ->
                val on = intensity == level
                Surface(
                    onClick = { intensity = level },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (on) c.accentSoft else c.surface,
                    border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.line),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = level.shortLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) c.accent else c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        GroupLabel("Longest call I'll take")
        Text(
            text = "${minutes.roundToInt()} minutes",
            style = MaterialTheme.typography.headlineSmall,
            color = c.ink,
        )
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = 5f..30f,
            steps = 4,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.line,
            ),
        )

        Box(Modifier.height(32.dp))

        PrimaryButton(
            text = "Save",
            onClick = { onSave(topics, intensity, minutes.roundToInt()) },
        )
    }
}

/** "Any (incl. Very Heavy)" does not fit a third of a phone; the stored name is unchanged. */
private fun MaxIntensity.shortLabel(): String = when (this) {
    MaxIntensity.LIGHT_ONLY -> "Light"
    MaxIntensity.UP_TO_HEAVY -> "Heavy"
    MaxIntensity.ANY -> "Anything"
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun BoundariesScreenPreview() {
    RedesignTheme(night = false) {
        BoundariesScreen(
            initialTopics = Topic.entries.toSet() - Topic.SOMETHING_HARD,
            initialIntensity = MaxIntensity.UP_TO_HEAVY,
            initialMaxMinutes = 15,
            onBack = {},
            onSave = { _, _, _ -> },
        )
    }
}
