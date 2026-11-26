package com.ongero.app.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryTokenStore
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryTokenStore(deviceId = "device-1")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun session() = SessionManager(
        ApiClient(baseUrl = server.url("/api/v1").toString()),
        store,
        now = { clock },
    )

    private fun authResponse(expiresIn: String = "15m", token: String = "tok") =
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"accessToken":"$token","expiresIn":"$expiresIn",
                "user":{"id":"user-9","anonymous":true,"subscriptionTier":"free"}}""",
        )

    @Test
    fun `stores the server's user id, which is not the app's local one`() = runTest {
        // CallManager generates its own UUID and keys the signalling connection on it.
        // The server's users.id is a different value, and it is the one a payment is
        // recorded against.
        server.enqueue(authResponse())

        session().withToken { ApiResult.Ok(Unit) }

        assertEquals("user-9", store.serverUserId)
    }

    @Test
    fun `reads expiresIn as a duration, not as a number`() = runTest {
        server.enqueue(authResponse(expiresIn = "15m"))

        session().withToken { ApiResult.Ok(Unit) }

        // "15m".toLong() is null and Number("15m") is NaN. Either would leave the
        // expiry at zero and re-authenticate on every single call.
        assertEquals(clock + 15 * 60_000L, store.expiresAtMillis)
    }

    @Test
    fun `refreshes a token that is about to expire, not only one that has`() = runTest {
        store.accessToken = "nearly-dead"
        store.expiresAtMillis = clock + 30_000 // inside the one-minute headroom
        server.enqueue(authResponse(token = "fresh"))

        var seen: String? = null
        session().withToken { token -> seen = token; ApiResult.Ok(Unit) }

        // Without headroom a token valid at the moment of the check can expire while
        // the request is in flight on a slow connection.
        assertEquals("fresh", seen)
    }

    @Test
    fun `keeps the old token when authentication fails on the network`() = runTest {
        store.accessToken = "old"
        store.expiresAtMillis = 0 // expired, so a refresh is attempted
        server.shutdown() // nothing is listening

        val result = session().withToken { ApiResult.Ok(Unit) }

        assertTrue(result is ApiResult.Err)
        assertEquals(ApiFailure.NETWORK, (result as ApiResult.Err).failure)
        // A token with some life left beats none if the failure was the network
        // rather than the server rejecting us.
        assertEquals("old", store.accessToken)
    }

    @Test
    fun `refuses a response with a blank token rather than caching it`() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"accessToken":"","expiresIn":"15m","user":{"id":"u"}}"""),
        )

        val result = session().withToken { ApiResult.Ok(Unit) }

        assertEquals(ApiFailure.MALFORMED, (result as ApiResult.Err).failure)
        assertEquals(null, store.accessToken)
    }
}

class ParseDurationTest {

    @Test
    fun `reads the jsonwebtoken formats the server config uses`() {
        assertEquals(15 * 60_000L, parseDurationMillis("15m"))
        assertEquals(7 * 86_400_000L, parseDurationMillis("7d"))
        assertEquals(3_600_000L, parseDurationMillis("1h"))
        assertEquals(90_000L, parseDurationMillis("90s"))
        assertEquals(250L, parseDurationMillis("250ms"))
        assertEquals(60_000L, parseDurationMillis(" 1M "))
    }

    @Test
    fun `reads a bare number as seconds, the way jsonwebtoken does`() {
        assertEquals(3_600_000L, parseDurationMillis("3600"))
    }

    @Test
    fun `falls back rather than returning zero for anything it cannot read`() {
        // Zero would mean "already expired", and every call would re-authenticate.
        val default = SessionManager.DEFAULT_EXPIRY_MS
        listOf(null, "", "   ", "soon", "15 minutes", "-5m", "1.5h").forEach {
            assertEquals("input: $it", default, parseDurationMillis(it))
        }
    }
}
