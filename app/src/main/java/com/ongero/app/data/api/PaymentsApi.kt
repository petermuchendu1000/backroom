package com.ongero.app.data.api

import android.util.Log

/**
 * Plan values the server accepts.
 *
 * Separate from [com.ongero.app.ui.screens.settings.SubscriptionPlan] on purpose. That
 * enum's `id` is "plus_monthly", a display identifier; the API validates `plan` with
 * @IsIn(['monthly','yearly']) and a global pipe that rejects anything else with a 400.
 * The two have drifted apart in this codebase once already, on the Ask screen's topic
 * labels, where the mismatch failed silently instead of loudly.
 */
enum class PlanWire(val wire: String) {
    MONTHLY("monthly"),
    YEARLY("yearly"),
}

/** How a payment ended, from the client's point of view. */
sealed interface PaymentOutcome {
    /** The gateway settled it and the subscription is active. */
    data class Succeeded(val receipt: String?) : PaymentOutcome

    /** Terminal and unpaid: declined, cancelled at the PIN prompt, or timed out. */
    data class Failed(val reason: String) : PaymentOutcome

    /** We stopped waiting. The payment may still land; the webhook settles it. */
    data object StillPending : PaymentOutcome
}

/**
 * Buying Ongero Plus.
 *
 * Two calls, because that is what an STK Push is: one to put the prompt on the
 * person's phone, then waiting. The result reaches the server by webhook, so polling
 * is reading our own record rather than asking Safaricom — except when the webhook has
 * not arrived, where the server reconciles against the gateway for us.
 */
class PaymentsApi(
    private val api: ApiClient,
    private val session: SessionManager,
) {
    /**
     * Send the M-Pesa prompt. Returns the correlation id to wait on.
     *
     * The number is normalised here and not only in the caller. A screen validating
     * its own input is good manners; this is the last point before the request leaves
     * the device, and it is the one place every caller has to pass through.
     */
    suspend fun startPayment(
        plan: PlanWire,
        phoneNumber: String,
    ): ApiResult<InitiatePaymentResponse> {
        val normalised = PhoneNumbers.normalise(phoneNumber)
            ?: return ApiResult.Err(
                ApiFailure.CLIENT,
                "Enter a Safaricom number, like 0712 345 678",
            )

        return session.withToken { token ->
            api.post(
                path = "payments/pay",
                body = InitiatePaymentRequest(phoneNumber = normalised, plan = plan.wire),
                type = InitiatePaymentResponse::class.java,
                bearer = token,
            )
        }
    }

    suspend fun paymentStatus(paymentRef: String): ApiResult<PaymentStatusResponse> =
        session.withToken { token ->
            api.get(
                // The ref is a gateway id (a UUID for PalPluss, ws_CO_… for Daraja).
                // Encoded because it reaches us from the server, not from a constant.
                path = "payments/${java.net.URLEncoder.encode(paymentRef, "UTF-8")}/status",
                type = PaymentStatusResponse::class.java,
                bearer = token,
            )
        }

    /**
     * Wait for the person to deal with the prompt.
     *
     * [delay] is injected so tests do not actually sleep. Polling stops at
     * [timeoutMillis] and reports StillPending rather than Failed, because a prompt
     * that has not been answered is not a payment that failed — the webhook will
     * settle it either way and the next launch will see the subscription.
     *
     * A network blip does not end the wait: the next poll is the retry.
     */
    suspend fun awaitOutcome(
        paymentRef: String,
        pollIntervalMillis: Long = POLL_INTERVAL_MS,
        timeoutMillis: Long = POLL_TIMEOUT_MS,
        now: () -> Long = System::currentTimeMillis,
        delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): PaymentOutcome {
        val deadline = now() + timeoutMillis

        while (now() < deadline) {
            delay(pollIntervalMillis)

            when (val result = paymentStatus(paymentRef)) {
                is ApiResult.Ok -> {
                    val body = result.value
                    if (body.succeeded) {
                        return PaymentOutcome.Succeeded(body.mpesaReceipt)
                    }
                    if (body.status != STATUS_PENDING) {
                        return PaymentOutcome.Failed(reasonFor(body.status))
                    }
                }

                is ApiResult.Err -> {
                    // Keep waiting. A failed poll says nothing about the payment, and
                    // giving up here would tell someone their money did not arrive
                    // when it may have.
                    Log.w(TAG, "Poll for $paymentRef failed: ${result.failure}")
                }
            }
        }
        return PaymentOutcome.StillPending
    }

    private fun reasonFor(status: String): String = when (status) {
        "cancelled" -> "The prompt was cancelled."
        "failed" -> "The payment did not go through."
        else -> "The payment did not complete."
    }

    companion object {
        private const val TAG = "PaymentsApi"
        private const val STATUS_PENDING = "pending"

        /**
         * Four seconds. The webhook usually settles this within a second or two of the
         * PIN, so most of the wait is the person finding their phone. While the row is
         * still pending the server reconciles against the gateway on every poll, so a
         * tighter interval would spend the gateway's rate limit on nothing.
         */
        const val POLL_INTERVAL_MS = 4_000L

        /** Two and a half minutes. An unanswered STK prompt expires before this. */
        const val POLL_TIMEOUT_MS = 150_000L
    }
}
