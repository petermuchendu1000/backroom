package com.example.backroom.ui.redesign.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.backroom.data.network.CallManager
import com.example.backroom.data.network.ConnectionState
import com.example.backroom.ui.redesign.screens.HomeScreen
import com.example.backroom.ui.screens.home.IncomingPreviewDialog

/**
 * Home, bound to [CallManager].
 *
 * The listener path is preserved deliberately: the original Home is the only place that
 * turns an incoming match into a call for a listener, and the only host of the incoming
 * preview dialog. Replacing the screen without carrying both over would silently strand
 * every listener, so the effect below mirrors the original exactly and the existing
 * dialog is reused rather than reimplemented.
 *
 * Not carried over: the availability switch. The redesigned Home has no listener toggle,
 * so with the flag on a listener cannot go on call from this screen — "Listen instead"
 * opens boundaries. See the commit message.
 */
@Composable
fun RedesignHome(
    onStartCall: () -> Unit,
    onListen: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onCallStarted: (callId: String) -> Unit,
) {
    val connectionState by CallManager.signalingClient.connectionState.collectAsState()
    val matchInfo by CallManager.matchInfo.collectAsState()
    val matchConsumed by CallManager.matchConsumed.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()
    val incomingPreview by CallManager.incomingPreview.collectAsState()

    LaunchedEffect(matchInfo, matchConsumed, currentRole) {
        if (matchInfo != null && !matchConsumed && currentRole == "listener") {
            if (CallManager.tryConsumeMatch()) {
                onCallStarted(matchInfo!!.callId)
            }
        }
    }

    incomingPreview?.let { preview ->
        IncomingPreviewDialog(
            preview = preview,
            onAccept = { CallManager.acceptPreview() },
            onDecline = { CallManager.declinePreview() },
        )
    }

    HomeScreen(
        connected = connectionState == ConnectionState.CONNECTED,
        onStart = onStartCall,
        onListen = onListen,
        onSettings = onSettings,
        onHelp = onHelp,
    )
}
