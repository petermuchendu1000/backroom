package com.ongero.app.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Where the session lives between launches.
 *
 * An interface so SessionManager can be tested on the JVM. Android's SharedPreferences
 * returns stubbed nulls under unit tests with isReturnDefaultValues, which would make
 * every token test pass for the wrong reason.
 */
interface TokenStore {
    /** Stable per-install identity. The server keys its user record on this. */
    val deviceId: String
    var accessToken: String?
    var expiresAtMillis: Long
    var serverUserId: String?
}

class SharedPrefsTokenStore(private val context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Generated once and kept, rather than read from the device.
     *
     * ANDROID_ID would be the obvious source and is deliberately not used: this is an
     * anonymous product, and a value shared with every other app on the handset is a
     * handle someone could correlate against. A random UUID identifies the install to
     * us and to nobody else. It survives updates and dies with an uninstall, which is
     * the right lifetime for an anonymous account.
     */
    override val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: adopt(legacyId() ?: UUID.randomUUID().toString())

    private fun adopt(id: String): String {
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /**
     * The UUID CallManager used to generate for itself, before any of this existed.
     *
     * Reused as the device id on an install that predates the session layer, so that
     * install keeps one server-side account instead of silently becoming a second
     * user — and anything already bought under it stays bought.
     */
    private fun legacyId(): String? = context.applicationContext
        .getSharedPreferences("ongero_prefs", Context.MODE_PRIVATE)
        .getString("anonymous_user_id", null)

    override var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    override var expiresAtMillis: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    override var serverUserId: String?
        get() = prefs.getString(KEY_SERVER_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_SERVER_USER_ID, value).apply()

    private companion object {
        const val PREFS = "ongero_session"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "token_expires_at"
        const val KEY_SERVER_USER_ID = "server_user_id"
    }
}

/** In-memory store for tests and previews. */
class InMemoryTokenStore(
    override val deviceId: String = "test-device",
    override var accessToken: String? = null,
    override var expiresAtMillis: Long = 0L,
    override var serverUserId: String? = null,
) : TokenStore

/**
 * Holds the bearer token, and is the only thing that knows how to get another one.
 *
 * Tokens are short-lived — the server's default is 15 minutes — so almost every call
 * after an idle period would 401 if each call site had to cope on its own. Instead
 * [withToken] hands out a token, runs the call, and on a 401 authenticates once and
 * runs it again. Exactly once: a loop here would hammer the server with a bad device id.
 *
 * Authentication is serialised by a mutex. Without it, two screens resuming together
 * both see an expired token, both POST /auth/anonymous, and the second overwrites the
 * first — harmless today, but it is two user rows away from being a real bug.
 */
class SessionManager(
    private val api: ApiClient,
    private val store: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** The server's id for this install, once authenticated. Null before that. */
    val serverUserId: String? get() = store.serverUserId

    /**
     * A bearer token, for callers that cannot wrap their work in [withToken].
     *
     * The WebSocket is the reason this exists: its credential goes on the handshake,
     * so there is no request to retry — a rejected token has to be replaced before the
     * next connection attempt rather than after a failed call. Pass forceRefresh=true
     * when the server has just rejected the one we had.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): ApiResult<String> =
        token(forceRefresh)

    /**
     * Run [call] with a valid bearer token, authenticating first if needed and once
     * more if the server rejects the token we had.
     */
    suspend fun <T : Any> withToken(call: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val token = when (val t = token(forceRefresh = false)) {
            is ApiResult.Ok -> t.value
            is ApiResult.Err -> return t
        }

        val first = call(token)
        if (first !is ApiResult.Err || first.failure != ApiFailure.UNAUTHORIZED) {
            return first
        }

        // The token was stale or revoked. One fresh attempt, then give up.
        Log.i(TAG, "Token rejected; re-authenticating once")
        val refreshed = when (val t = token(forceRefresh = true)) {
            is ApiResult.Ok -> t.value
            is ApiResult.Err -> return t
        }
        return call(refreshed)
    }

    private suspend fun token(forceRefresh: Boolean): ApiResult<String> = mutex.withLock {
        if (!forceRefresh) {
            val cached = store.accessToken
            // A minute of headroom, so a token that is valid when we check cannot
            // expire in flight on a slow connection.
            if (cached != null && store.expiresAtMillis - EXPIRY_HEADROOM_MS > now()) {
                return@withLock ApiResult.Ok(cached)
            }
        }

        val result = api.post(
            path = "auth/anonymous",
            body = AnonymousAuthRequest(deviceId = store.deviceId),
            type = AuthResponse::class.java,
        )

        when (result) {
            is ApiResult.Err -> {
                // The old token is kept. If this failure was the network rather than
                // the server, a token that still has life in it is better than none.
                Log.w(TAG, "Anonymous authentication failed: ${result.failure}")
                result
            }

            is ApiResult.Ok -> {
                val auth = result.value
                if (auth.accessToken.isBlank()) {
                    return@withLock ApiResult.Err(
                        ApiFailure.MALFORMED,
                        "The server did not return a session.",
                    )
                }
                store.accessToken = auth.accessToken
                store.serverUserId = auth.user.id
                store.expiresAtMillis = now() + parseDurationMillis(auth.expiresIn)
                Log.i(TAG, "Authenticated as ${auth.user.id}")
                ApiResult.Ok(auth.accessToken)
            }
        }
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val EXPIRY_HEADROOM_MS = 60_000L

        /** What the server sends when it does not say: its own jwt.expiresIn default. */
        internal const val DEFAULT_EXPIRY_MS = 15 * 60_000L
    }
}

/**
 * Read a jsonwebtoken duration — "15m", "7d", "3600" — into milliseconds.
 *
 * The server sends `expiresIn` straight from its config, which is a string in that
 * format and not a number of seconds. Treating "15m" as a number gives NaN, and a token
 * with no expiry is one that is never refreshed until something 401s.
 */
internal fun parseDurationMillis(value: String?, default: Long = SessionManager.DEFAULT_EXPIRY_MS): Long {
    val text = value?.trim()?.lowercase() ?: return default
    if (text.isEmpty()) return default

    val match = Regex("^(\\d+)\\s*(ms|s|m|h|d)?$").find(text) ?: return default
    val amount = match.groupValues[1].toLongOrNull() ?: return default
    return when (match.groupValues[2]) {
        "ms" -> amount
        "s" -> amount * 1_000
        "m" -> amount * 60_000
        "h" -> amount * 3_600_000
        "d" -> amount * 86_400_000
        // Bare number: jsonwebtoken reads it as seconds, not milliseconds.
        else -> amount * 1_000
    }
}
