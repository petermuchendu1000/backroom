package com.ongero.app.ui.redesign.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongero.app.ui.redesign.components.Hairline
import com.ongero.app.ui.redesign.components.PrimaryButton
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors

/**
 * `label` is what the person reads; `wire` is what the server is already sent.
 *
 * They are deliberately separate. The redesign softens the wording ("A lot" rather
 * than "Very Heavy"), but listener boundaries and matching are keyed on the existing
 * strings, so changing the copy must not change the protocol. RedesignWireMappingTest
 * asserts every `wire` value still exists in the original enums.
 */
enum class Weight(val label: String, val wire: String) {
    LIGHT("Light", "Light"),
    HEAVY("Heavy", "Heavy"),
    A_LOT("A lot", "Very Heavy"),
}

enum class Topic(val label: String, val wire: String) {
    CONFESSION("Confession", "Confession"),
    LETTING_OUT("Letting out", "Letting Out"),
    ADVICE("Advice", "Advice"),
    GRIEF("Grief", "Grief"),
    JUST_TALKING("Just talking", "Just Talking"),
    HARD_TO_SAY("Hard to say", "Something Hard"),
}

/** What the sharer flow submits. */
data class Ask(
    val topic: Topic?,
    val weight: Weight,
    val note: String,
    val minutes: Int,
)

/**
 * The single screen that replaces Topic, Tone, Write-15-words, Length and Review.
 *
 * Everything here is optional except intensity, which is the only field matching
 * actually needs. Duration is a default you can change, not a step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    onBack: () -> Unit,
    onFindSomeone: (Ask) -> Unit,
    onChangeDuration: () -> Unit = {},
    minutes: Int = 10,
    initialTopic: Topic? = Topic.LETTING_OUT,
    initialWeight: Weight = Weight.HEAVY,
) {
    val c = ongeroColors
    var topic by remember { mutableStateOf(initialTopic) }
    var weight by remember { mutableStateOf(initialWeight) }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = c.muted,
                modifier = Modifier.size(22.dp),
            )
        }

        Box(Modifier.height(20.dp))

        Text(
            text = "What's going on?",
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink,
        )

        Box(Modifier.height(28.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Topic.entries.forEach { t ->
                Chip(
                    label = t.label,
                    selected = topic == t,
                    onClick = { topic = if (topic == t) null else t },
                )
            }
        }

        Box(Modifier.height(36.dp))

        Text("How heavy is it?", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Box(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Weight.entries.forEach { w ->
                Segment(
                    label = w.label,
                    selected = weight == w,
                    onClick = { weight = w },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(Modifier.height(36.dp))

        Text("A few words, if you want", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Box(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = {
                Text(
                    "They'll read this first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted.copy(alpha = 0.7f),
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = c.surface,
                focusedContainerColor = c.surface,
                unfocusedBorderColor = c.line,
                focusedBorderColor = c.accent,
                unfocusedTextColor = c.ink,
                focusedTextColor = c.ink,
            ),
        )

        Box(
            Modifier
                .weight(1f)
                .height(16.dp),
        )

        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "About $minutes minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            TextButton(onClick = onChangeDuration) {
                Text("Change", style = MaterialTheme.typography.bodyMedium, color = c.accent)
            }
        }
        Box(Modifier.height(10.dp))

        PrimaryButton(
            text = "Find someone",
            onClick = { onFindSomeone(Ask(topic, weight, note, minutes)) },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = ongeroColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) c.accent else c.surface,
        border = BorderStroke(1.dp, if (selected) c.accent else c.line),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) c.onAccent else c.ink,
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ongeroColors
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) c.accent else c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AskScreenPreview() {
    RedesignTheme(night = false) { AskScreen(onBack = {}, onFindSomeone = {}) }
}
