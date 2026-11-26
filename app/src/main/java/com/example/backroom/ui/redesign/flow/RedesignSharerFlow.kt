package com.example.backroom.ui.redesign.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.backroom.data.network.CallManager
import com.example.backroom.ui.redesign.screens.Ask
import com.example.backroom.ui.redesign.screens.AskScreen
import com.example.backroom.ui.redesign.screens.WaitingScreen

private enum class SharerStep { ASK, WAITING }

/**
 * The sharer flow: one question, then waiting.
 *
 * Replaces the five steps in SharerFlowScreen.kt (Topic, Tone, Intent, Duration,
 * Review). It submits the same four fields through the same
 * [CallManager.submitShare] call, so the server contract is unchanged — only the
 * number of screens a person passes through to get there.
 *
 * The match-handling below mirrors the existing WaitingScreen exactly: act only when a
 * match exists, is unconsumed, and this client is the sharer, and claim it through the
 * atomic [CallManager.tryConsumeMatch] so two components cannot both navigate.
 */
@Composable
fun RedesignSharerFlow(
    onBack: () -> Unit,
    onCallStarted: (callId: String) -> Unit,
) {
    var step by remember { mutableStateOf(SharerStep.ASK) }

    val matchInfo by CallManager.matchInfo.collectAsState()
    val matchConsumed by CallManager.matchConsumed.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()
    val shareExpiredReason by CallManager.shareExpiredReason.collectAsState()

    LaunchedEffect(shareExpiredReason) {
        if (shareExpiredReason != null) onBack()
    }

    LaunchedEffect(matchInfo, matchConsumed, currentRole) {
        val match = matchInfo ?: return@LaunchedEffect
        if (!matchConsumed && currentRole == "sharer" && CallManager.tryConsumeMatch()) {
            onCallStarted(match.callId)
        }
    }

    when (step) {
        SharerStep.ASK -> AskScreen(
            onBack = onBack,
            onFindSomeone = { ask -> submit(ask); step = SharerStep.WAITING },
        )

        SharerStep.WAITING -> WaitingScreen(
            onCancel = {
                CallManager.cancelShare()
                onBack()
            },
        )
    }
}

/**
 * Maps the one-screen answer onto the wire fields the server already expects.
 *
 * Topic is optional in the redesign; when it is left blank the server still needs a
 * value, and "Just Talking" is the existing catch-all it already accepts.
 */
private fun submit(ask: Ask) {
    CallManager.submitShare(
        topic = ask.topic?.wire ?: "Just Talking",
        tone = ask.weight.wire,
        intent = ask.note,
        duration = ask.minutes,
    )
}
