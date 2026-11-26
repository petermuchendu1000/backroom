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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.Hairline
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.components.QuietButton
import com.example.backroom.ui.redesign.components.ToggleRow
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors
import com.example.backroom.ui.screens.feedback.ReportData
import com.example.backroom.ui.screens.feedback.ReportReason

/**
 * Report a problem.
 *
 * Reached straight after someone has left a call that went wrong, so it asks for the
 * least it can: one tap on a reason, and nothing else is required. Details are optional
 * and blocking is one switch, both below the fold of the decision.
 *
 * Submit stays disabled until a reason is chosen — the only required field — rather
 * than accepting an empty report and failing later.
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onSubmit: (ReportData) -> Unit,
) {
    val c = backroomColors
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    var block by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        Text(
            text = "What happened?",
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink,
        )

        Box(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportReason.entries.forEach { r ->
                ReasonRow(
                    label = r.label,
                    selected = reason == r,
                    onClick = { reason = r },
                )
            }
        }

        Box(Modifier.height(28.dp))

        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            placeholder = {
                Text(
                    "Anything else? (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted.copy(alpha = 0.7f),
                )
            },
            shape = RoundedCornerShape(14.dp),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = c.surface,
                focusedContainerColor = c.surface,
                unfocusedBorderColor = c.line,
                focusedBorderColor = c.accent,
                unfocusedTextColor = c.ink,
                focusedTextColor = c.ink,
            ),
        )

        Box(Modifier.height(20.dp))
        Hairline()
        ToggleRow(
            title = "Don't match me with them again",
            checked = block,
            onCheckedChange = { block = it },
        )
        Hairline()

        Box(Modifier.height(28.dp))

        PrimaryButton(
            text = "Send report",
            onClick = {
                reason?.let { onSubmit(ReportData(it, details, block)) }
            },
            container = if (reason == null) c.line else c.accent,
        )
        QuietButton(text = "Not now", onClick = onBack)
    }
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = backroomColors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.line),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) c.accent else c.ink,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun ReportScreenPreview() {
    RedesignTheme(night = false) { ReportScreen(onBack = {}, onSubmit = {}) }
}
