package com.ongero.app.ui.redesign.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ongero.app.data.PreferencesManager
import com.ongero.app.ui.payment.PlusPaymentViewModel
import com.ongero.app.ui.redesign.screens.BlockedPeopleScreen
import com.ongero.app.ui.redesign.screens.BoundariesScreen
import com.ongero.app.ui.redesign.screens.PlusScreen
import com.ongero.app.ui.screens.listener.MaxIntensity
import com.ongero.app.ui.screens.sharer.Topic
import com.ongero.app.ui.screens.settings.SubscriptionPlan

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

/**
 * Ongero Plus, bound to [PreferencesManager] for what is already owned and to
 * [PlusPaymentViewModel] for buying it.
 *
 * A ViewModel rather than remembered state because an STK Push takes as long as it
 * takes someone to find their phone and type a PIN. A rotation in the middle of that
 * would otherwise drop the poll, and the screen would have no idea a payment was in
 * flight — while the money moved anyway.
 */
@Composable
fun RedesignPlus(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    viewModel: PlusPaymentViewModel = viewModel(
        factory = PlusPaymentViewModel.factory(LocalContext.current),
    ),
) {
    val payment by viewModel.state.collectAsState()

    PlusScreen(
        activeTier = if (preferencesManager.isSubscriptionActive()) {
            preferencesManager.subscriptionTier
        } else {
            null
        },
        onBack = onBack,
        onSubscribe = { viewModel.begin() },
        payment = payment,
        onPay = { plan, number -> viewModel.pay(plan, number) },
        onCancelPayment = { viewModel.cancel() },
    )
}
