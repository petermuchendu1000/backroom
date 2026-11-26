package com.example.backroom.ui.redesign.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.backroom.data.network.CallManager
import com.example.backroom.ui.redesign.screens.InCallScreen
import com.example.backroom.ui.redesign.screens.LeavingCallScreen
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The call, bound to [CallManager].
 *
 * "Leaving a call" is a state of this screen rather than a separate destination, so the
 * call stays visible behind it and Stay is a genuine no-op rather than a navigation.
 */
@Composable
fun RedesignInCall(
    onEndCall: (elapsedSeconds: Int) -> Unit,
    onEmergencyExit: (elapsedSeconds: Int) -> Unit,
) {
    val topic by CallManager.currentCallTopic.collectAsState()
    val isMuted by CallManager.isMuted.collectAsState()
    val currentCallId by CallManager.currentCallId.collectAsState()

    var seconds by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var handledEnd by remember { mutableStateOf(false) }
    var sawCallId by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds += 1
        }
    }

    // The remote side hanging up clears currentCallId. Mirrors the existing screen's
    // guard: only treat null as "ended" after a real id has been seen, and only once.
    LaunchedEffect(currentCallId) {
        if (currentCallId != null) {
            sawCallId = true
        } else if (sawCallId && !handledEnd) {
            handledEnd = true
            onEndCall(seconds)
        }
    }

    val elapsed = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    InCallScreen(
        topic = topic ?: "Just talking",
        elapsed = elapsed,
        onMute = { CallManager.setMuted(!isMuted) },
        onEnd = {
            if (!handledEnd) {
                handledEnd = true
                CallManager.endCall()
                onEndCall(seconds)
            }
        },
        onFeelUnsafe = { leaving = true },
    )

    if (leaving) {
        LeavingCallScreen(
            elapsed = elapsed,
            onLeaveAndReport = {
                if (!handledEnd) {
                    handledEnd = true
                    CallManager.endCall()
                    onEmergencyExit(seconds)
                }
            },
            onLeaveQuietly = {
                if (!handledEnd) {
                    handledEnd = true
                    CallManager.endCall()
                    onEndCall(seconds)
                }
            },
            onStay = { leaving = false },
        )
    }
}
