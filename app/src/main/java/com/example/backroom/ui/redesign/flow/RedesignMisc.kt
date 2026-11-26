package com.example.backroom.ui.redesign.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.backroom.data.PreferencesManager
import com.example.backroom.ui.redesign.screens.BlockedPeopleScreen
import com.example.backroom.ui.redesign.screens.BoundariesScreen
import com.example.backroom.ui.redesign.screens.PlusScreen
import com.example.backroom.ui.screens.listener.MaxIntensity
import com.example.backroom.ui.screens.sharer.Topic
import com.example.backroom.ui.screens.settings.SubscriptionPlan

/**
 * Boundaries, bound to [PreferencesManager].
 *
 * Load and save use the ORIGINAL enum names, exactly as the existing screen does:
 * `Topic.name` and `MaxIntensity.name`. Anything else would write values the matcher
 * does not recognise, and the failure would be silent — a listener simply stops being
 * offered calls.
 */
@Composable
fun RedesignBoundaries(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val initialTopics = remember {
        preferencesManager.acceptedTopics.mapNotNull { name ->
            runCatching { Topic.valueOf(name) }.getOrNull()
        }.toSet()
    }
    val initialIntensity = remember {
        runCatching { MaxIntensity.valueOf(preferencesManager.maxIntensity) }
            .getOrDefault(MaxIntensity.UP_TO_HEAVY)
    }
    val initialMinutes = remember { preferencesManager.maxDurationMinutes }

    BoundariesScreen(
        initialTopics = initialTopics,
        initialIntensity = initialIntensity,
        initialMaxMinutes = initialMinutes,
        onBack = onBack,
        onSave = { topics, intensity, minutes ->
            preferencesManager.acceptedTopics = topics.map { it.name }.toSet()
            preferencesManager.maxIntensity = intensity.name
            preferencesManager.maxDurationMinutes = minutes
            onSaved()
        },
    )
}

/** Blocked people, bound to [PreferencesManager]. */
@Composable
fun RedesignBlockedPeople(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
) {
    var blocked by remember { mutableStateOf(preferencesManager.blockedUsers.toList()) }

    BlockedPeopleScreen(
        blocked = blocked,
        onBack = onBack,
        onUnblock = { id ->
            preferencesManager.blockedUsers = preferencesManager.blockedUsers - id
            blocked = preferencesManager.blockedUsers.toList()
        },
    )
}

/** Backroom Plus, bound to [PreferencesManager]. */
@Composable
fun RedesignPlus(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    onSubscribe: (SubscriptionPlan) -> Unit,
) {
    PlusScreen(
        activeTier = if (preferencesManager.isSubscriptionActive()) {
            preferencesManager.subscriptionTier
        } else {
            null
        },
        onBack = onBack,
        onSubscribe = onSubscribe,
    )
}
