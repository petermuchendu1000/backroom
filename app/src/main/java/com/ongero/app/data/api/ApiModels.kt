package com.ongero.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * Wire types for the Ongero REST API.
 *
 * Field names here are the JSON the server actually sends. Where a Kotlin name would
 * read better, @SerializedName pins the wire name rather than renaming the field and
 * hoping — Gson is silent when a field does not match, and a silently null token is
 * much harder to diagnose than a compile error.
 */

// ---------------------------------------------------------------- auth

data class AnonymousAuthRequest(
    val deviceId: String,
    val locale: String = "en",
)

data class AuthResponse(
    val accessToken: String,
    /** A jsonwebtoken duration such as "15m", not a number of seconds. */
    val expiresIn: String?,
    val user: AuthUser,
)

data class AuthUser(
    val id: String,
    val anonymous: Boolean = true,
    val subscriptionTier: String = "free",
)

// ---------------------------------------------------------------- payments

/**
 * The body of POST /payments/pay.
 *
 * `plan` must be exactly "monthly" or "yearly": the server validates it with
 * @IsIn(['monthly','yearly']) and the global pipe rejects anything else with a 400.
 * It is NOT the SubscriptionPlan.id ("plus_monthly"), which is a display identifier.
 * PaymentWireMappingTest exists to keep those two apart.
 */
data class InitiatePaymentRequest(
    val phoneNumber: String,
    val plan: String,
)

data class InitiatePaymentResponse(
    val success: Boolean = false,
    /** What to show while the prompt is on the person's screen. */
    val message: String? = null,
    /** "palpluss" or "daraja". Informational; the client does not branch on it. */
    val provider: String? = null,
    /** The gateway's correlation id. The only handle we have on this payment. */
    val paymentRef: String,
    /** Our short reference, the one that appears on an M-Pesa statement. */
    val reference: String? = null,
    val plan: String? = null,
    val amount: Int = 0,
)

data class PaymentStatusResponse(
    /** "pending" | "succeeded" | "failed" | "cancelled". */
    val status: String,
    val succeeded: Boolean = false,
    val paymentRef: String? = null,
    val reference: String? = null,
    val plan: String? = null,
    val amount: Int = 0,
    @SerializedName("receipt") val mpesaReceipt: String? = null,
    val completedAt: String? = null,
)

// ---------------------------------------------------------------- errors

/**
 * Nest's default error body. `message` is a string for a thrown HttpException and an
 * array of strings when the validation pipe rejects a field, so it is read as a raw
 * element and flattened rather than typed as one or the other.
 */
data class ApiErrorBody(
    val statusCode: Int = 0,
    val error: String? = null,
    val message: Any? = null,
)
