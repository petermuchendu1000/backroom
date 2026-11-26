package com.ongero.app.ui.payment

import com.ongero.app.data.api.PlanWire
import com.ongero.app.ui.screens.settings.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Display identifiers are not wire values.
 *
 * This codebase has already shipped that mistake once: the redesigned Ask screen's
 * topic labels read "Letting out" while the matcher expects "Letting Out", and nothing
 * failed — the request simply never matched anyone.
 *
 * Here the same drift is louder but no easier to spot from the UI. SubscriptionPlan.id
 * is "plus_monthly"; InitiatePaymentDto validates `plan` with
 * @IsIn(['monthly','yearly']) behind a pipe configured forbidNonWhitelisted, so
 * sending the id is a 400 with no clue in the app about why nobody can subscribe.
 *
 * These assertions are the contract. Changing either side without the other fails here.
 */
class PaymentWireMappingTest {

    @Test
    fun `every plan maps to a value the API accepts`() {
        // Copied from backend/rest-api/src/modules/payments/dto/payment.dto.ts.
        val accepted = setOf("monthly", "yearly")

        SubscriptionPlan.entries.forEach { plan ->
            assertEquals(
                "plan ${plan.name} must map to a value the API accepts",
                true,
                plan.wire().wire in accepted,
            )
        }
    }

    @Test
    fun `the wire value is not the display id`() {
        assertEquals("plus_monthly", SubscriptionPlan.MONTHLY.id)
        assertEquals("monthly", SubscriptionPlan.MONTHLY.wire().wire)

        assertEquals("plus_yearly", SubscriptionPlan.YEARLY.id)
        assertEquals("yearly", SubscriptionPlan.YEARLY.wire().wire)
    }

    @Test
    fun `distinct plans do not collapse onto one wire value`() {
        val wires = SubscriptionPlan.entries.map { it.wire() }.toSet()
        assertEquals(SubscriptionPlan.entries.size, wires.size)
    }

    @Test
    fun `the prices the screen shows are the ones the server charges`() {
        // backend/rest-api/src/config/configuration.ts:
        //   SUBSCRIPTION_MONTHLY_PRICE default 500, SUBSCRIPTION_YEARLY_PRICE 5000.
        // The server is the authority — it ignores any amount the client sends — so a
        // mismatch here means the screen quotes one price and M-Pesa asks for another.
        assertEquals(500, SubscriptionPlan.MONTHLY.priceKES)
        assertEquals(5000, SubscriptionPlan.YEARLY.priceKES)
    }
}
