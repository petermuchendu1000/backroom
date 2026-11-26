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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.GroupLabel
import com.example.backroom.ui.redesign.components.PrimaryButton
import com.example.backroom.ui.redesign.components.ScreenHeader
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors
import com.example.backroom.ui.screens.settings.SubscriptionPlan

private val PLUS_FEATURES = listOf(
    "Calls up to 30 minutes",
    "Matched sooner",
    "More voice options",
    "Take more calls as a listener",
    "No ads",
)

/**
 * Backroom Plus.
 *
 * The price is the headline, because that is the question. Features are a short list
 * underneath in plain words — "Matched sooner" rather than "Priority matching" — and
 * "Support development" is dropped: it is a reason the developer cares about, not a
 * feature someone is buying.
 */
@Composable
fun PlusScreen(
    activeTier: String?,
    onBack: () -> Unit,
    onSubscribe: (SubscriptionPlan) -> Unit,
) {
    val c = backroomColors
    var plan by remember { mutableStateOf(SubscriptionPlan.MONTHLY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "Backroom Plus", onBack = onBack)

        if (activeTier != null) {
            Box(Modifier.height(20.dp))
            Text(
                text = "Active · $activeTier",
                style = MaterialTheme.typography.bodyMedium,
                color = c.calm,
            )
        }

        GroupLabel("Plan")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubscriptionPlan.entries.forEach { p ->
                PlanRow(plan = p, selected = plan == p, onClick = { plan = p })
            }
        }

        GroupLabel("What you get")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PLUS_FEATURES.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(feature, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                }
            }
        }

        Box(Modifier.height(32.dp))

        PrimaryButton(
            text = if (activeTier != null) "Change plan" else "Subscribe",
            onClick = { onSubscribe(plan) },
        )
    }
}

@Composable
private fun PlanRow(plan: SubscriptionPlan, selected: Boolean, onClick: () -> Unit) {
    val c = backroomColors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KES ${plan.priceKES} ${plan.period}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                )
                plan.savings?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.calm)
                }
            }
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun PlusScreenPreview() {
    RedesignTheme(night = false) {
        PlusScreen(activeTier = null, onBack = {}, onSubscribe = {})
    }
}
