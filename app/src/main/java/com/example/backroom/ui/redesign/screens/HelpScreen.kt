package com.example.backroom.ui.redesign.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.GroupLabel
import com.example.backroom.ui.redesign.components.Hairline
import com.example.backroom.ui.redesign.components.ScreenHeader
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors
import com.example.backroom.ui.screens.help.CrisisResource
import com.example.backroom.ui.screens.help.FaqItem

/**
 * Help.
 *
 * This is the one screen where "minimal text" must not mean "less information", so the
 * reduction is in ordering and disclosure rather than in what is said.
 *
 * The disclaimer moved. It currently sits at the very bottom, after every FAQ answer,
 * which is the last place someone in trouble will reach. It belongs beside the numbers
 * it is talking about, so it is now the line under the heading.
 *
 * Crisis numbers are the first thing on the screen and each one is a single large target
 * that dials. Descriptions are kept for these — knowing which number does what matters
 * here — and dropped for the specialised list, where the category already says it.
 * FAQ answers start collapsed: they were previously all expanded at once, which is what
 * pushed the disclaimer off the bottom of the screen.
 */
@Composable
fun HelpScreen(
    crisis: List<CrisisResource>,
    more: List<CrisisResource>,
    faqs: List<FaqItem>,
    onCall: (phone: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = backroomColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "Need help now?", onBack = onBack)

        Box(Modifier.height(16.dp))

        Text(
            text = "Backroom isn't a crisis service. These people are.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )

        Box(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            crisis.forEach { resource ->
                CallRow(
                    name = resource.name,
                    detail = resource.description,
                    hours = resource.hours,
                    onCall = { onCall(resource.phone) },
                )
            }
        }

        if (more.isNotEmpty()) {
            GroupLabel("More help")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                more.forEach { resource ->
                    CallRow(
                        name = resource.name,
                        detail = resource.category.displayName,
                        hours = null,
                        onCall = { onCall(resource.phone) },
                    )
                }
            }
        }

        if (faqs.isNotEmpty()) {
            GroupLabel("Questions")
            Hairline()
            faqs.forEach { faq ->
                FaqRow(question = faq.question, answer = faq.answer)
                Hairline()
            }
        }
    }
}

/** One number. The whole row dials, so the target is the row, not an icon. */
@Composable
private fun CallRow(
    name: String,
    detail: String,
    hours: String?,
    onCall: () -> Unit,
) {
    val c = backroomColors
    Surface(
        onClick = onCall,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Call $name" },
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                Text(
                    text = if (hours != null) "$detail · $hours" else detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            Icon(
                Icons.Outlined.Call,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A question whose answer stays out of the way until it is asked for. */
@Composable
private fun FaqRow(question: String, answer: String) {
    val c = backroomColors
    var open by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Hide answer" else "Show answer",
                tint = c.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Text(
                text = answer.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 1100, showBackground = true)
@Composable
private fun HelpScreenPreview() {
    RedesignTheme(night = false) {
        HelpScreen(
            crisis = com.example.backroom.ui.screens.help.KenyaCrisisResources.primaryResources,
            more = com.example.backroom.ui.screens.help.KenyaCrisisResources.specializedResources,
            faqs = com.example.backroom.ui.screens.help.BackroomFaq.allFaqs,
            onCall = {},
            onBack = {},
        )
    }
}
