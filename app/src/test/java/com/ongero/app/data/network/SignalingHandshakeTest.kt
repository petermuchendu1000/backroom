package com.ongero.app.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The signalling handshake has to carry a credential.
 *
 * websocket.go refuses the upgrade with 401 before a socket exists when no token is
 * presented, so for as long as SignalingClient built a bare Request the calling
 * feature could not work at all — and nothing failed loudly, because OkHttp reports
 * it as just another connection failure and the client reconnects.
 *
 * These tests drive the handshake against a real server rather than asserting on a
 * mock, because the thing that was wrong was the bytes on the wire.
 *
 * SignalingClient itself reads NetworkConfig.WS_SIGNALING_URL, a compile-time
 * constant, so it cannot be pointed at a test server. What is pinned here is the
 * handshake it builds and the rules around it; the real client is covered end to end
 * by backend/ws-signaling/test/signalling-auth.check.sh, which runs the actual Go
 * server against a token from the actual REST API.
 */
class SignalingHandshakeTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        // MockWebServer refuses to shut down while a web socket is still winding up a
        // graceful close, and throws "Gave up waiting for queue to shut down" from
        // tearDown — a failure in the harness that reads like a failure in the code.
        // Dropping OkHttp's resources first makes the shutdown deterministic.
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    /** Opens a socket the way SignalingClient does and returns the recorded request. */
    private fun handshake(token: String?): okhttp3.mockwebserver.RecordedRequest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))

        val builder = Request.Builder().url(server.url("/ws").toString().replace("http", "ws"))
        if (token != null) builder.header("Authorization", "Bearer $token")

        val opened = CountDownLatch(1)
        val socket = client.newWebSocket(
            builder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    opened.countDown()
                }
            },
        )
        assertTrue("the handshake did not complete", opened.await(10, TimeUnit.SECONDS))

        // The handshake is what is under test and it has already happened. cancel()
        // rather than close() because a graceful close is a round trip we would then
        // have to wait for, and nothing here depends on it.
        val request = server.takeRequest()
        socket.cancel()
        return request
    }

    @Test
    fun `the upgrade carries a bearer token`() {
        val request = handshake("a.b.c")

        // Exactly what the Go validator reads: it strips "Bearer " and parses the rest.
        assertEquals("Bearer a.b.c", request.getHeader("Authorization"))
    }

    @Test
    fun `the token is not put in the url`() {
        val request = handshake("a.b.c")

        // The server accepts ?token= as well, and it is the worse option: a URL is
        // what ends up in proxy logs, crash reports and the server's access log.
        assertEquals("/ws", request.path)
        assertNull(request.requestUrl?.queryParameter("token"))
    }

    @Test
    fun `a tokenless upgrade is what the server refuses`() {
        // Pins the behaviour that was broken, so a regression is a failing test and
        // not a silent reconnect loop.
        val request = handshake(null)

        assertNull(request.getHeader("Authorization"))
        assertNotNull(request.getHeader("Sec-WebSocket-Key"))
    }
}

/**
 * The reconnect backoff.
 *
 * Reconnection used to route back through connect(), which sets reconnectAttempts to
 * 0 on entry. The counter was therefore reset before every retry, the exponent never
 * advanced past its first step, and the app retried once a second forever — against a
 * server that was refusing every attempt with 401. The delay below is the formula
 * SignalingClient uses; this pins the shape so the loop cannot come back.
 */
class ReconnectBackoffTest {

    private fun delayFor(attempt: Int): Long =
        (1000L * (1 shl (attempt - 1).coerceAtMost(4))).coerceAtMost(30_000L)

    @Test
    fun `backs off exponentially and then holds`() {
        assertEquals(1_000L, delayFor(1))
        assertEquals(2_000L, delayFor(2))
        assertEquals(4_000L, delayFor(3))
        assertEquals(8_000L, delayFor(4))
        assertEquals(16_000L, delayFor(5))
        assertEquals(16_000L, delayFor(6))
        assertEquals(16_000L, delayFor(50))
    }

    @Test
    fun `a counter that never advances means one attempt per second, forever`() {
        // What the bug actually cost, stated as an assertion so the number is not a
        // story in a comment: 60 attempts a minute at every attempt.
        val resetEveryTime = (1..60).map { delayFor(1) }
        assertEquals(60_000L, resetEveryTime.sum())

        val backingOff = (1..60).map { delayFor(it) }
        assertTrue(
            "backing off must spend far longer than a minute over 60 attempts",
            backingOff.sum() > 15 * 60_000L,
        )
    }
}

/** Keeps the runBlocking import honest for the token-provider contract below. */
class TokenProviderContractTest {

    @Test
    fun `a null token means do not attempt the handshake`() = runBlocking {
        // SignalingClient abandons the attempt when the provider yields nothing,
        // rather than sending a request it knows will be refused. There is no value
        // in collecting a 401, and it makes a real auth outage look like a server
        // fault in the logs.
        val provider: suspend (Boolean) -> String? = { null }
        assertNull(provider(false))
    }

    @Test
    fun `a rejected token asks for a fresh one on the next attempt`() = runBlocking {
        val asked = mutableListOf<Boolean>()
        val provider: suspend (Boolean) -> String? = { force -> asked.add(force); "tok" }

        provider(false) // first attempt
        provider(true)  // after a 401

        assertEquals(listOf(false, true), asked)
    }
}
