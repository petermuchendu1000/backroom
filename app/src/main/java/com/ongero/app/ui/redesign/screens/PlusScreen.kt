package com.ongero.app.ui.redesign.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ongero.app.ui.payment.PlusPaymentState
import com.ongero.app.ui.redesign.components.GroupLabel
import com.ongero.app.ui.redesign.components.OutlineButton
import com.ongero.app.ui.redesign.components.PrimaryButton
import com.ongero.app.ui.redesign.components.QuietButton
import com.ongero.app.ui.redesign.components.Rings
import com.ongero.app.ui.redesign.components.ScreenHeader
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.redesign.theme.ongeroColors
import com.ongero.app.ui.screens.settings.SubscriptionPlan

private val PLUS_FEATURES = listOf(
    "Calls up to 30 minutes",
    "Matched sooner",
    "More voice options",
    "Take more calls as a listener",
    "No ads",
)

/**
 * Ongero Plus.
 *
 * The price is the headline, because that is the question. Features are a short list
 * underneath in plain words — "Matched sooner" rather than "Priority matching" — and
 * "Support development" is dropped: it is a reason the developer cares about, not a
 * feature someone is buying.
 *
 * Paying takes over the screen rather than opening a dialog on top of it. Someone who
 * has just sent money should not be looking at the thing they were about to buy with a
 * spinner over it; they should be looking at what is happening to their money, and
 * nothing else.
 *
 * Stateless by design: [payment] comes from PlusPaymentViewModel, so every state below
 * can be rendered in a screenshot test without a network or a gateway.
 */
@Composable
fun PlusScreen(
    activeTier: String?,
    onBack: () -> Unit,
    onSubscribe: (SubscriptionPlan) -> Unit,
    payment: PlusPaymentState = PlusPaymentState.Idle,
    onPay: (SubscriptionPlan, String) -> Unit = { _, _ -> },
    onCancelPayment: () -> Unit = {},
) {
    val c = ongeroColors
    var plan by remember { mutableStateOf(SubscriptionPlan.MONTHLY) }

    // Once money is moving, the plan picker is no longer the subject of the screen.
    when (payment) {
        is PlusPaymentState.Sending,
        is PlusPaymentState.Waiting,
        is PlusPaymentState.Succeeded,
        is PlusPaymentState.Failed,
        PlusPaymentState.StillPending,
        -> {
            PaymentProgress(
                payment = payment,
                plan = plan,
                onDone = onCancelPayment,
                onBack = onBack,
            )
            return
        }

        else -> Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 52.dp, bottom = 40.dp),
    ) {
        ScreenHeader(title = "Ongero Plus", onBack = onBack)

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

        if (payment is PlusPaymentState.NeedsPhone) {
            PhoneStep(
                error = payment.error,
                amountKes = plan.priceKES,
                onPay = { number -> onPay(plan, number) },
                onCancel = onCancelPayment,
            )
        } else {
            PrimaryButton(
                text = if (activeTier != null) "Change plan" else "Subscribe",
                onClick = { onSubscribe(plan) },
            )
        }
    }
}

/**
 * The number the prompt goes to.
 *
 * Asked for at the last possible moment and never stored. Ongero knows a phone number
 * only because a payment cannot be reconciled without one, and asking here — rather
 * than at sign-up — keeps that true for everyone who never pays.
 */
@Composable
private fun PhoneStep(
    error: String?,
    amountKes: Int,
    onPay: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = ongeroColors
    var number by remember { mutableStateOf("") }

    Text(
        text = "Your M-Pesa number",
        style = MaterialTheme.typography.bodyLarge,
        color = c.ink,
    )
    Box(Modifier.height(12.dp))

    OutlinedTextField(
        value = number,
        onValueChange = { number = it },
        singleLine = true,
        placeholder = { Text("0712 345 678", color = c.muted) },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.line,
            errorBorderColor = c.danger,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    if (error != null) {
        Box(Modifier.height(10.dp))
        Text(error, style = MaterialTheme.typography.bodySmall, color = c.danger)
    }

    Box(Modifier.height(20.dp))
    PrimaryButton(text = "Send prompt · KES $amountKes", onClick = { onPay(number) })
    QuietButton(text = "Not now", onClick = onCancel)
}

/**
 * What is happening to the money, and nothing else.
 *
 * The waiting state reuses the rings the rest of the app waits with, so this does not
 * feel like a different product bolted on at the moment someone is asked to pay.
 */
@Composable
private fun PaymentProgress(
    payment: PlusPaymentState,
    plan: SubscriptionPlan,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val c = ongeroColors

    val headline: String
    val detail: String
    val tone = when (payment) {
        is PlusPaymentState.Failed -> c.danger
        is PlusPaymentState.Succeeded -> c.calm
        else -> c.muted
    }

    when (payment) {
        is PlusPaymentState.Sending -> {
            headline = "One moment"
            detail = "Asking M-Pesa to send you a prompt."
        }

        is PlusPaymentState.Waiting -> {
            headline = "Check your phone"
            detail = "Enter your M-Pesa PIN to pay KES ${payment.amountKes}."
        }

        is PlusPaymentState.Succeeded -> {
            headline = "You're on Plus"
            detail = payment.receipt?.let { "M-Pesa receipt $it" }
                ?: "Your subscription is active."
        }

        is PlusPaymentState.Failed -> {
            headline = "Not paid"
            detail = payment.reason
        }

        PlusPaymentState.StillPending -> {
            headline = "Still waiting"
            // Not "it failed". The payment may yet land, and the server will apply it
            // whether or not this screen is open.
            detail = "If you paid, Plus will be on next time you open Ongero."
        }

        else -> {
            headline = ""
            detail = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .padding(horizontal = 32.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Rings(
            sizes = listOf(188.dp, 132.dp, 84.dp),
            colors = listOf(c.accentSofter, c.accentSoft, c.accentMid),
            pulse = payment is PlusPaymentState.Waiting || payment is PlusPaymentState.Sending,
        )

        Box(Modifier.height(44.dp))
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Box(Modifier.height(14.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = tone)

        Box(Modifier.height(52.dp))

        when (payment) {
            is PlusPaymentState.Succeeded -> PrimaryButton(text = "Done", onClick = onBack)

            is PlusPaymentState.Failed -> {
                OutlineButton(text = "Try again", onClick = onDone)
                QuietButton(text = "Not now", onClick = onBack)
            }

            PlusPaymentState.StillPending -> PrimaryButton(text = "Close", onClick = onBack)

            // Nothing to offer while the prompt is live. Leaving is still possible —
            // the back arrow is gone on purpose, but the hardware back is not, and the
            // payment settles server-side regardless.
            else -> QuietButton(text = "Cancel", onClick = onDone)
        }
    }
}

@Composable
private fun PlanRow(plan: SubscriptionPlan, selected: Boolean, onClick: () -> Unit) {
    val c = ongeroColors
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
