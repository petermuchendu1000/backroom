package com.ongero.app.data.network

import com.ongero.app.BuildConfig

/**
 * Network configuration for Ongero backend services.
 *
 * Automatically switches between development and production based on build type.
 */
object NetworkConfig {

    // ============================================
    // ENVIRONMENT CONFIGURATION
    // ============================================

    /**
     * Set to true when deploying to production.
     * This can also be tied to BuildConfig.DEBUG
     */
    // A release build is always production. A debug build can be sent there too, with
    // -PprodBackend=true, which is how you test against api.ongero.com from a device
    // while keeping an installable, debuggable APK. See app/build.gradle.kts.
    private val IS_PRODUCTION = !BuildConfig.DEBUG || BuildConfig.USE_PROD_BACKEND

    // ============================================
    // DEVELOPMENT SETTINGS (Local WiFi)
    // Change SERVER_IP to your computer's local IP
    // ============================================

    private const val DEV_SERVER_IP = "192.168.1.160"
    private const val DEV_REST_API_URL = "http://$DEV_SERVER_IP:8080/api/v1"
    private const val DEV_WS_SIGNALING_URL = "ws://$DEV_SERVER_IP:8443/ws"
    private const val DEV_TURN_URL = "turn:$DEV_SERVER_IP:3478"

    // ============================================
    // PRODUCTION SETTINGS (Public Internet)
    // Update PROD_DOMAIN to your actual domain
    // ============================================

    // ongero.com is the domain we actually hold. This said ongero.llc until the
    // Backroom -> Ongero rename was corrected: backroom.llc was never ours either, and
    // renaming the brand inside it produced a domain nobody owns.
    private const val PROD_DOMAIN = "ongero.com"
    private const val PROD_REST_API_URL = "https://api.$PROD_DOMAIN/api/v1"
    private const val PROD_WS_SIGNALING_URL = "wss://ws.$PROD_DOMAIN/ws"
    private const val PROD_TURN_URL = "turn:turn.$PROD_DOMAIN:3478"

    // ============================================
    // ACTIVE CONFIGURATION
    // ============================================

    /** REST API base URL */
    val REST_API_URL: String
        get() = if (IS_PRODUCTION) PROD_REST_API_URL else DEV_REST_API_URL

    /** WebSocket signaling server URL */
    val WS_SIGNALING_URL: String
        get() = if (IS_PRODUCTION) PROD_WS_SIGNALING_URL else DEV_WS_SIGNALING_URL

    /** TURN server URL */
    val TURN_URL: String
        get() = if (IS_PRODUCTION) PROD_TURN_URL else DEV_TURN_URL

    /** STUN server URL (using Google's public STUN for reliability) */
    const val STUN_URL = "stun:stun.l.google.com:19302"

    // ============================================
    // TURN CREDENTIALS
    // ============================================
    //
    // There are none here, deliberately. coturn runs in use-auth-secret mode and the
    // signalling server mints a short-lived credential per peer, delivered on the
    // matchMade message (SignalingClient.TurnServer). A username and password
    // compiled into the APK would be extractable by anyone who installs it, would
    // never expire, and would be the same for every user.

    // ============================================
    // TIMEOUTS
    // ============================================

    const val WS_CONNECT_TIMEOUT_MS = 10_000L
    const val WS_READ_TIMEOUT_MS = 30_000L
    const val API_TIMEOUT_MS = 30_000L

    /**
     * For debugging - print current configuration
     */
    fun getConfigSummary(): String {
        return """
            Environment: ${if (IS_PRODUCTION) "PRODUCTION" else "DEVELOPMENT"}
            REST API: $REST_API_URL
            WebSocket: $WS_SIGNALING_URL
            TURN: $TURN_URL
        """.trimIndent()
    }
}

