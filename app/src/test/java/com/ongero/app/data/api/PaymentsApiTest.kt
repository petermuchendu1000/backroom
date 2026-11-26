package com.ongero.app.data.api

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The payment client, against a real HTTP server.
 *
 * MockWebServer rather than a mock of OkHttp, because the things that break here are
 * on the wire: a header that is not sent, a JSON field whose name does not match, a
 * path that is off by a segment. A mocked client would agree with whatever this code
 * believes and prove nothing.
 *
 * The response bodies below are copied from what the Nest API actually returns — see
 * PaymentsController.pay and .status in backend/rest-api.
 */
class PaymentsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var payments: PaymentsApi
    private lateinit var store: InMemoryTokenStore
    private val gson = Gson()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = ApiClient(baseUrl = server.url("/api/v1").toString())
        store = InMemoryTokenStore(deviceId = "device-under-test")
        payments = PaymentsApi(api, SessionManager(api, store))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun authBody(token: String = "tok-1") = """
        {"accessToken":"$token","expiresIn":"15m",
         "user":{"id":"11111111-2222-3333-4444-555555555555","anonymous":true,
                 "subscriptionTier":"free","createdAt":"2026-01-01T00:00:00.000Z"}}
    """.trimIndent()

    private fun payBody() = """
        {"success":true,"message":"Please check your phone and enter your M-Pesa PIN",
         "provider":"palpluss","paymentRef":"fa98a577-95ea-4a8f-8467-1fbe74f5d6f4",
         "reference":"ONGB7K2M9XQ4","plan":"monthly","amount":500}
    """.trimIndent()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun RecordedRequest.jsonBody(): Map<*, *> =
        gson.fromJson(body.readUtf8(), Map::class.java)

    // ------------------------------------------------------------------ starting

    @Test
    fun `authenticates first, then pays with the bearer it was given`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(payBody()))

        val result = payments.startPayment(PlanWire.MONTHLY, "0712345678")

        assertTrue(result is ApiResult.Ok)
        val auth = server.takeRequest()
        assertEquals("POST", auth.method)
        assertEquals("/api/v1/auth/anonymous", auth.path)
        assertEquals("device-under-test", auth.jsonBody()["deviceId"])
        // No token exists yet, so nothing should be claiming one.
        assertNull(auth.getHeader("Authorization"))

        val pay = server.takeRequest()
        assertEquals("POST", pay.method)
        assertEquals("/api/v1/payments/pay", pay.path)
        assertEquals("Bearer tok-1", pay.getHeader("Authorization"))
    }

    @Test
    fun `sends the plan value the server validates against, not the display id`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(payBody()))

        payments.startPayment(PlanWire.YEARLY, "0712345678")
        server.takeRequest()

        // InitiatePaymentDto has @IsIn(['monthly','yearly']) behind a global pipe with
        // forbidNonWhitelisted. "plus_yearly" would be a 400, and nothing in the app
        // would say why.
        assertEquals("yearly", server.takeRequest().jsonBody()["plan"])
    }

    @Test
    fun `sends the number normalised, whatever was typed`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(payBody()))

        payments.startPayment(PlanWire.MONTHLY, "  +254 712 345 678 ")
        server.takeRequest()

        assertEquals("254712345678", server.takeRequest().jsonBody()["phoneNumber"])
    }

    /**
     * Writes the exact request this client makes to build/android-contract/, so it can
     * be replayed against the real NestJS API instead of being transcribed by hand.
     *
     * Transcription is how a contract check ends up testing what someone believed the
     * client sends. backend/rest-api/test/android-contract.db-check.sh reads these
     * files and POSTs them at a running server; if the field names or the plan value
     * are wrong, the server's validation pipe says so rather than this test agreeing
     * with itself.
     */
    @Test
    fun `records the request bytes for the server-side contract check`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(payBody()))

        payments.startPayment(PlanWire.MONTHLY, "0712345678")
        val auth = server.takeRequest()
        val pay = server.takeRequest()

        val dir = java.io.File("build/android-contract").apply { mkdirs() }
        java.io.File(dir, "auth-request.json").writeText(auth.body.readUtf8())
        java.io.File(dir, "pay-request.json").writeText(pay.body.readUtf8())
        java.io.File(dir, "pay-path.txt").writeText(pay.path ?: "")

        assertTrue(java.io.File(dir, "pay-request.json").readText().contains("\"plan\""))
    }

    @Test
    fun `reuses a cached token instead of authenticating per call`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(payBody()))
        server.enqueue(json(payBody()))

        payments.startPayment(PlanWire.MONTHLY, "0712345678")
        payments.startPayment(PlanWire.MONTHLY, "0712345678")

        // auth, pay, pay — not auth, pay, auth, pay.
        assertEquals(3, server.requestCount)
        assertEquals("/api/v1/auth/anonymous", server.takeRequest().path)
        assertEquals("/api/v1/payments/pay", server.takeRequest().path)
        assertEquals("/api/v1/payments/pay", server.takeRequest().path)
    }

    @Test
    fun `re-authenticates once when the token is rejected, then gives up`() = runTest {
        store.accessToken = "stale"
        store.expiresAtMillis = Long.MAX_VALUE

        server.enqueue(json("""{"statusCode":401,"message":"Unauthorized"}""", 401))
        server.enqueue(json(authBody("tok-2")))
        server.enqueue(json("""{"statusCode":401,"message":"Unauthorized"}""", 401))

        val result = payments.startPayment(PlanWire.MONTHLY, "0712345678")

        assertTrue(result is ApiResult.Err)
        assertEquals(ApiFailure.UNAUTHORIZED, (result as ApiResult.Err).failure)
        // Exactly one retry. A loop here would hammer the server with a bad device id.
        assertEquals(3, server.requestCount)
        assertEquals("Bearer stale", server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/anonymous", server.takeRequest().path)
        assertEquals("Bearer tok-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `surfaces the server's own words when it refuses a payment`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(
            json("""{"statusCode":400,"message":"That phone number is not a valid M-Pesa number"}""", 400),
        )

        val result = payments.startPayment(PlanWire.MONTHLY, "0712345678")

        assertTrue(result is ApiResult.Err)
        result as ApiResult.Err
        assertEquals(ApiFailure.CLIENT, result.failure)
        assertEquals("That phone number is not a valid M-Pesa number", result.message)
    }

    @Test
    fun `flattens the validation pipe's array of messages`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(
            json("""{"statusCode":400,"message":["plan must be one of: monthly, yearly"],"error":"Bad Request"}""", 400),
        )

        val result = payments.startPayment(PlanWire.MONTHLY, "0712345678")

        assertEquals(
            "plan must be one of: monthly, yearly",
            (result as ApiResult.Err).message,
        )
    }

    @Test
    fun `reports a gateway outage as something to retry, not as a refusal`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json("""{"statusCode":503,"message":"Payment service is temporarily unavailable"}""", 503))

        val result = payments.startPayment(PlanWire.MONTHLY, "0712345678")

        assertEquals(ApiFailure.SERVER, (result as ApiResult.Err).failure)
    }

    // ------------------------------------------------------------------ waiting

    private fun statusBody(status: String, succeeded: Boolean, receipt: String? = null) = """
        {"status":"$status","succeeded":$succeeded,"paymentRef":"ref-1",
         "reference":"ONGB7K2M9XQ4","plan":"monthly","amount":500,
         "receipt":${if (receipt == null) "null" else "\"$receipt\""},"completedAt":null}
    """.trimIndent()

    /** No real sleeping, and a clock the test moves itself. */
    private suspend fun await(ref: String = "ref-1"): PaymentOutcome {
        var clock = 0L
        return payments.awaitOutcome(
            paymentRef = ref,
            pollIntervalMillis = 1_000,
            timeoutMillis = 10_000,
            now = { clock },
            delay = { clock += it },
        )
    }

    @Test
    fun `waits through pending polls and reports the receipt on success`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(statusBody("pending", false)))
        server.enqueue(json(statusBody("pending", false)))
        server.enqueue(json(statusBody("succeeded", true, "LGR019G3J2")))

        val outcome = await()

        assertEquals(PaymentOutcome.Succeeded("LGR019G3J2"), outcome)
    }

    @Test
    fun `stops at the first terminal failure`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(statusBody("failed", false)))

        val outcome = await()

        assertTrue(outcome is PaymentOutcome.Failed)
        // auth + one poll. It must not keep asking after a terminal answer.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed poll does not end the wait`() = runTest {
        server.enqueue(json(authBody()))
        // A dropped connection mid-wait says nothing about the payment. Treating it as
        // failure would tell someone their money did not arrive when it may have.
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        server.enqueue(json(statusBody("succeeded", true, "LGR019G3J2")))

        assertEquals(PaymentOutcome.Succeeded("LGR019G3J2"), await())
    }

    @Test
    fun `gives up as still pending, never as failed`() = runTest {
        server.enqueue(json(authBody()))
        repeat(12) { server.enqueue(json(statusBody("pending", false))) }

        // An unanswered prompt is not a refused payment: the webhook settles it
        // server-side whether or not this screen is still watching.
        assertEquals(PaymentOutcome.StillPending, await())
    }

    @Test
    fun `asks about the payment it was given, url-encoded`() = runTest {
        server.enqueue(json(authBody()))
        server.enqueue(json(statusBody("succeeded", true)))

        await("ws_CO_191220191020363925")
        server.takeRequest()

        assertEquals(
            "/api/v1/payments/ws_CO_191220191020363925/status",
            server.takeRequest().path,
        )
    }
}
