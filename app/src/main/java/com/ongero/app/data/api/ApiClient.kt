package com.ongero.app.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ongero.app.data.network.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why a call did not produce a value. The caller decides what each one means. */
enum class ApiFailure {
    /** No answer: offline, DNS, timeout, TLS. Worth retrying. */
    NETWORK,

    /** 401. The token is missing, expired or rejected; get a new one and retry once. */
    UNAUTHORIZED,

    /** 4xx other than 401. The request was wrong; retrying it unchanged will not help. */
    CLIENT,

    /** 5xx, or the gateway reporting itself unavailable. Worth retrying later. */
    SERVER,

    /** A 2xx whose body was not the JSON we expected. */
    MALFORMED,
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    data class Err(
        val failure: ApiFailure,
        /** Safe to show a person. Never a stack trace, never a token. */
        val message: String,
        val httpStatus: Int? = null,
    ) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onOk(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Ok) block(value)
    return this
}

/**
 * The Ongero REST API, over OkHttp.
 *
 * Deliberately small: no Retrofit, no code generation, no new dependency. OkHttp and
 * Gson are already in the build for the signalling client, and the API surface this app
 * needs is four endpoints.
 *
 * It knows nothing about tokens beyond putting a bearer on the wire when it is given
 * one. Obtaining and refreshing tokens is [SessionManager]'s job, so that a 401 is
 * handled in exactly one place instead of at every call site.
 */
class ApiClient(
    private val baseUrl: String = NetworkConfig.REST_API_URL,
    private val http: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun <T : Any> get(
        path: String,
        type: Class<T>,
        bearer: String? = null,
    ): ApiResult<T> = execute(Request.Builder().url(url(path)).get(), type, bearer)

    suspend fun <T : Any> post(
        path: String,
        body: Any,
        type: Class<T>,
        bearer: String? = null,
    ): ApiResult<T> = execute(
        Request.Builder().url(url(path)).post(gson.toJson(body).toRequestBody(JSON)),
        type,
        bearer,
    )

    private fun url(path: String) = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private suspend fun <T : Any> execute(
        builder: Request.Builder,
        type: Class<T>,
        bearer: String?,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        builder.header("Accept", "application/json")

        val request = builder.build()
        try {
            http.newCall(request).execute().use { response ->
                // Read once: an OkHttp body is a one-shot stream, and reading it twice
                // to log then parse yields an empty string the second time.
                val text = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext ApiResult.Err(
                        failureFor(response.code),
                        describe(response.code, text),
                        response.code,
                    )
                }

                val parsed = try {
                    gson.fromJson(text, type)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "${request.method} ${request.url.encodedPath}: unparseable body", e)
                    null
                }

                if (parsed == null) {
                    return@withContext ApiResult.Err(
                        ApiFailure.MALFORMED,
                        "The server sent something we could not read.",
                        response.code,
                    )
                }
                ApiResult.Ok(parsed)
            }
        } catch (e: IOException) {
            // Covers offline, DNS failure, connection reset and both timeouts. The
            // exception's own text is for the log; the person gets plain words.
            Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.message}")
            ApiResult.Err(ApiFailure.NETWORK, "No connection. Check your network and try again.")
        }
    }

    private fun failureFor(code: Int): ApiFailure = when {
        code == 401 -> ApiFailure.UNAUTHORIZED
        code in 500..599 -> ApiFailure.SERVER
        // 503 is already covered above; the gateway reports its own outages that way.
        else -> ApiFailure.CLIENT
    }

    /**
     * A sentence from the server's error body, or a generic one.
     *
     * Nest sends `message` as a string for a thrown exception and as an array of
     * strings when the validation pipe rejects fields, so both shapes are flattened.
     * Nothing from the body is shown without being turned into a plain sentence first.
     */
    private fun describe(code: Int, text: String): String {
        val fallback = when {
            code == 401 -> "Please try again."
            code in 500..599 -> "Something went wrong at our end. Try again shortly."
            else -> "That did not work."
        }
        if (text.isBlank()) return fallback
        return try {
            val body = gson.fromJson(text, ApiErrorBody::class.java)
            when (val m = body?.message) {
                is String -> m.ifBlank { fallback }
                is List<*> -> m.filterIsInstance<String>().firstOrNull() ?: fallback
                else -> fallback
            }
        } catch (_: JsonSyntaxException) {
            fallback
        }
    }

    companion object {
        private const val TAG = "ApiClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NetworkConfig.API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NetworkConfig.API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            // An STK prompt is not idempotent. OkHttp's default retry would re-send a
            // POST after a connection failure and could charge someone twice.
            .retryOnConnectionFailure(false)
            .build()
    }
}
