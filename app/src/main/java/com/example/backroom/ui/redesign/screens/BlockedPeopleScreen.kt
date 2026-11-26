package com.example.backroom.ui.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.components.Hairline
import com.example.backroom.ui.redesign.components.ScreenHeader
import com.example.backroom.ui.redesign.theme.RedesignTheme
import com.example.backroom.ui.redesign.theme.backroomColors

/**
 * Blocked people.
 *
 * Identifiers are anonymous and meaningless to read, so each row leads with the one
 * thing that is true and useful — that this person will not be matched again — and the
 * id is secondary.
 */
@Composable
fun BlockedPeopleScreen(
    blocked: List<String>,
    onBack: () -> Unit,
    onUnblock: (String) -> Unit,
) {
    val c = backroomColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "Blocked people", onBack = onBack)

        Box(Modifier.height(24.dp))

        if (blocked.isEmpty()) {
            Text(
                text = "Nobody is blocked.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
            )
        } else {
            Hairline()
            blocked.forEach { id ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Won't be matched again",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.ink,
                        )
                        Text(
                            text = id,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                        )
                    }
                    TextButton(onClick = { onUnblock(id) }) {
                        Text(
                            "Unblock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.accent,
                        )
                    }
                }
                Hairline()
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BlockedPeopleScreenPreview() {
    RedesignTheme(night = false) {
        BlockedPeopleScreen(
            blocked = listOf("anon_8f2c41", "anon_1d9b07"),
            onBack = {},
            onUnblock = {},
        )
    }
}
