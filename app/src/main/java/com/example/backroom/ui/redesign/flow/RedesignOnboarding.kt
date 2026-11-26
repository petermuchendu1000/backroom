package com.example.backroom.ui.redesign.flow

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.backroom.ui.redesign.screens.MicPermissionScreen
import com.example.backroom.ui.redesign.screens.WelcomeScreen

private enum class OnboardingStep { WELCOME, MICROPHONE }

/**
 * The whole of onboarding: two screens.
 *
 * Replaces the five-step chain in OnboardingScreen.kt (Welcome, How It Works, Safety,
 * Microphone, Notifications). How It Works and Safety are the three guarantees on
 * Welcome. Notifications belongs at the moment someone turns on listening, which is the
 * only point at which it means anything, so it is not asked here.
 *
 * Declining the microphone still completes onboarding: the app is reachable, and the
 * permission is asked again when a call is actually started. Blocking entry on it would
 * strand someone who tapped the wrong button.
 */
@Composable
fun RedesignOnboarding(onComplete: () -> Unit) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }

    val requestMic = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> onComplete() }

    when (step) {
        OnboardingStep.WELCOME -> WelcomeScreen(onBegin = { step = OnboardingStep.MICROPHONE })
        OnboardingStep.MICROPHONE -> MicPermissionScreen(
            onAllow = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
            onSkip = onComplete,
        )
    }
}
