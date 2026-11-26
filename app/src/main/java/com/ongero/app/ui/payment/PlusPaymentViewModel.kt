package com.ongero.app.ui.payment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ongero.app.data.PreferencesManager
import com.ongero.app.data.api.ApiClient
import com.ongero.app.data.api.ApiResult
import com.ongero.app.data.api.PaymentOutcome
import com.ongero.app.data.api.PaymentsApi
import com.ongero.app.data.api.PhoneNumbers
import com.ongero.app.data.api.PlanWire
import com.ongero.app.data.api.SessionManager
import com.ongero.app.data.api.SharedPrefsTokenStore
import com.ongero.app.ui.screens.settings.SubscriptionPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the Plus screen is in the business of buying something.
 *
 * Deliberately few states. Paying by M-Pesa is: ask for the number, send the prompt,
 * wait, done or not done. Anything finer would be describing our plumbing to someone
 * who is trying to buy a subscription.
 */
sealed interface PlusPaymentState {
    /** Nothing started. The plan picker is the whole screen. */
    data object Idle : PlusPaymentState

    /** Subscribe was tapped; we need a number before anything can happen. */
    data class NeedsPhone(val error: String? = null) : PlusPaymentState

    /** Talking to the gateway. Short — a second or two. */
    data object Sending : PlusPaymentState

    /** The prompt is on their phone. This is the long wait. */
    data class Waiting(val amountKes: Int) : PlusPaymentState

    data class Succeeded(val receipt: String?) : PlusPaymentState

    data class Failed(val reason: String) : PlusPaymentState

    /**
     * We stopped waiting, but the payment was never refused.
     *
     * Distinct from Failed on purpose: the webhook settles it server-side whether or
     * not the app is still watching, so telling someone it failed could be a lie about
     * their money.
     */
    data object StillPending : PlusPaymentState
}

class PlusPaymentViewModel(
    private val payments: PaymentsApi,
    private val preferences: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow<PlusPaymentState>(PlusPaymentState.Idle)
    val state: StateFlow<PlusPaymentState> = _state.asStateFlow()

    /** Kept so a second tap cannot start a second prompt while one is in flight. */
    private var inFlight: Job? = null

    /** Subscribe tapped. We do not have a phone number yet; ask for one. */
    fun begin() {
        if (inFlight?.isActive == true) return
        _state.value = PlusPaymentState.NeedsPhone()
    }

    fun cancel() {
        inFlight?.cancel()
        inFlight = null
        _state.value = PlusPaymentState.Idle
    }

    /**
     * Send the prompt and wait for the outcome.
     *
     * The number is validated here rather than on the server's say-so, so a typo
     * costs nothing. Guarded against a double tap: an STK Push is not idempotent and
     * two of them is two charges.
     */
    fun pay(plan: SubscriptionPlan, phoneNumber: String) {
        if (inFlight?.isActive == true) return

        val normalised = PhoneNumbers.normalise(phoneNumber)
        if (normalised == null) {
            _state.value = PlusPaymentState.NeedsPhone("Enter a Safaricom number, like 0712 345 678")
            return
        }

        _state.value = PlusPaymentState.Sending
        inFlight = viewModelScope.launch {
            when (val started = payments.startPayment(plan.wire(), normalised)) {
                is ApiResult.Err -> _state.value = PlusPaymentState.Failed(started.message)

                is ApiResult.Ok -> {
                    val amount = if (started.value.amount > 0) started.value.amount else plan.priceKES
                    _state.value = PlusPaymentState.Waiting(amount)

                    _state.value = when (val outcome = payments.awaitOutcome(started.value.paymentRef)) {
                        is PaymentOutcome.Succeeded -> {
                            // Cached so the UI is right immediately. The server's
                            // derived tier is still the authority; this is a copy that
                            // saves a round trip on the next screen.
                            preferences.subscriptionTier = "plus"
                            PlusPaymentState.Succeeded(outcome.receipt)
                        }

                        is PaymentOutcome.Failed -> PlusPaymentState.Failed(outcome.reason)
                        PaymentOutcome.StillPending -> PlusPaymentState.StillPending
                    }
                }
            }
        }
    }

    companion object {
        /**
         * No DI framework in this project, so the graph is assembled here. It is three
         * objects and they have no state worth sharing beyond the token store, which
         * is backed by SharedPreferences and so is shared anyway.
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val api = ApiClient()
                    val session = SessionManager(api, SharedPrefsTokenStore(appContext))
                    return PlusPaymentViewModel(
                        payments = PaymentsApi(api, session),
                        preferences = PreferencesManager(appContext),
                    ) as T
                }
            }
        }
    }
}

/**
 * The value the API accepts, which is not the enum's `id`.
 *
 * SubscriptionPlan.id is "plus_monthly" — a display identifier. The server validates
 * `plan` against exactly ['monthly','yearly'] and its global pipe 400s anything else.
 * PaymentWireMappingTest pins this mapping so the two cannot drift.
 */
fun SubscriptionPlan.wire(): PlanWire = when (this) {
    SubscriptionPlan.MONTHLY -> PlanWire.MONTHLY
    SubscriptionPlan.YEARLY -> PlanWire.YEARLY
}
