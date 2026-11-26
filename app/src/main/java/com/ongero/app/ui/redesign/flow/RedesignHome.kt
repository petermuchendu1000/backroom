package com.ongero.app.ui.redesign.flow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ongero.app.data.network.CallManager
import com.ongero.app.data.network.ConnectionState
import kotlinx.coroutines.delay
import com.ongero.app.service.ListenerService
import com.ongero.app.ui.redesign.screens.HomeMode
import com.ongero.app.ui.redesign.screens.IncomingPreviewSheet
import com.ongero.app.ui.redesign.screens.HomeScreen

/**
 * Home, bound to [CallManager].
 *
 * The listener path is preserved deliberately: the original Home is the only place that
 * turns an incoming match into a call for a listener, and the only host of the incoming
 * preview dialog. Replacing the screen without carrying both over would silently strand
 * every listener, so the effect below mirrors the original exactly and the existing
 * dialog is reused rather than reimplemented.
 *
 * Notifications are requested HERE, the first time someone makes themselves available,
 * rather than during onboarding. It is the only moment at which the permission means
 * anything: being told that someone needs to talk is the whole point of being available.
 * Declining it still makes the person available — the app simply cannot wake them.
 */
@Composable
fun RedesignHome(
    onStartCall: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onCallStarted: (callId: String) -> Unit,
) {
    val context = LocalContext.current

    val connectionState by CallManager.signalingClient.connectionState.collectAsState()
    val matchInfo by CallManager.matchInfo.collectAsState()
    val matchConsumed by CallManager.matchConsumed.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()
    val incomingPreview by CallManager.incomingPreview.collectAsState()
    val available by CallManager.isListenerAvailable.collectAsState()

    var mode by remember { mutableStateOf(HomeMode.TALK) }

    // Granted or not, the person still becomes available: the permission buys a
    // notification, not the ability to listen.
    val askNotifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> CallManager.setRoleListener(true) }

    val setAvailable: (Boolean) -> Unit = { wanted ->
        if (!wanted) {
            CallManager.setRoleListener(false)
        } else if (needsNotificationPermission(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            CallManager.setRoleListener(true)
        }
    }

    // Mirrors the original Home: the foreground service is what keeps a listener
    // reachable once the screen is gone.
    LaunchedEffect(available) {
        if (available) ListenerService.startListening(context)
        else ListenerService.stopListening(context)
    }

    LaunchedEffect(matchInfo, matchConsumed, currentRole) {
        if (matchInfo != null && !matchConsumed && currentRole == "listener") {
            if (CallManager.tryConsumeMatch()) {
                onCallStarted(matchInfo!!.callId)
            }
        }
    }

    // An incoming preview takes the whole screen rather than sitting in a dialog: it is
    // the listener's real decision, and their words deserve the room.
    incomingPreview?.let { preview ->
        var secondsLeft by remember(preview.shareId) {
            mutableIntStateOf(preview.countdownSeconds)
        }
        LaunchedEffect(preview.shareId) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
        }
        IncomingPreviewSheet(
            theirWords = preview.previewText,
            topic = preview.topic,
            tone = preview.tone,
            minutes = preview.durationMinutes,
            secondsLeft = secondsLeft,
            totalSeconds = preview.countdownSeconds,
            onAccept = { CallManager.acceptPreview() },
            onSkip = { CallManager.declinePreview() },
        )
        return
    }

    HomeScreen(
        connected = connectionState == ConnectionState.CONNECTED,
        onStart = onStartCall,
        onSettings = onSettings,
        onHelp = onHelp,
        mode = mode,
        available = available,
        onSwitchMode = { mode = it },
        onSetAvailable = setAvailable,
    )
}

/** POST_NOTIFICATIONS only exists, and is only refusable, from API 33. */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
