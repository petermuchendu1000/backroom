package com.ongero.app.ui.payment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The payment flow for the ORIGINAL subscription screen.
 *
 * The redesign gives paying a whole screen, because that is the right amount of room
 * for the moment someone sends money. The original design has no such screen and is
 * not being redesigned here, so it gets a dialog: the same ViewModel, the same states,
 * the same server calls, in the idiom the old screen already uses.
 *
 * The alternative was to wire only the redesign. That is still behind
 * BuildConfig.REDESIGN_UI and off by default, which would have left the Subscribe
 * button dead in every build anyone actually installs.
 */
@Composable
fun PaymentDialog(
    state: PlusPaymentState,
    onPay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is PlusPaymentState.Idle) return

    var number by remember { mutableStateOf("") }

    // While the prompt is live, dismissing by tapping outside would hide a payment
    // that is still happening. The only way out is the explicit button.
    val dismissable = state !is PlusPaymentState.Sending && state !is PlusPaymentState.Waiting

    AlertDialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        title = { Text(titleFor(state)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(bodyFor(state))

                if (state is PlusPaymentState.NeedsPhone) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        singleLine = true,
                        isError = state.error != null,
                        placeholder = { Text("0712 345 678") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }

                if (state is PlusPaymentState.Sending || state is PlusPaymentState.Waiting) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is PlusPaymentState.NeedsPhone ->
                    TextButton(onClick = { onPay(number) }) { Text("Send prompt") }

                is PlusPaymentState.Failed ->
                    TextButton(onClick = onDismiss) { Text("Try again") }

                is PlusPaymentState.Succeeded, PlusPaymentState.StillPending ->
                    TextButton(onClick = onDismiss) { Text("Close") }

                // Sending or Waiting: nothing to confirm.
                else -> Unit
            }
        },
        dismissButton = {
            if (dismissable && state is PlusPaymentState.NeedsPhone) {
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

private fun titleFor(state: PlusPaymentState): String = when (state) {
    is PlusPaymentState.NeedsPhone -> "Your M-Pesa number"
    is PlusPaymentState.Sending -> "One moment"
    is PlusPaymentState.Waiting -> "Check your phone"
    is PlusPaymentState.Succeeded -> "You're on Plus"
    is PlusPaymentState.Failed -> "Not paid"
    PlusPaymentState.StillPending -> "Still waiting"
    PlusPaymentState.Idle -> ""
}

private fun bodyFor(state: PlusPaymentState): String = when (state) {
    is PlusPaymentState.NeedsPhone ->
        state.error ?: "We'll send a payment prompt to this number."

    is PlusPaymentState.Sending -> "Asking M-Pesa to send you a prompt."
    is PlusPaymentState.Waiting -> "Enter your M-Pesa PIN to pay KES ${state.amountKes}."
    is PlusPaymentState.Succeeded ->
        state.receipt?.let { "M-Pesa receipt $it" } ?: "Your subscription is active."

    is PlusPaymentState.Failed -> state.reason
    // Not "it failed": the payment may still land, and the server applies it whether
    // or not this screen is open.
    PlusPaymentState.StillPending ->
        "If you paid, Plus will be on next time you open Ongero."

    PlusPaymentState.Idle -> ""
}
