package com.ongero.app.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ongero.app.data.PreferencesManager
import com.ongero.app.data.network.CallManager
import com.ongero.app.BuildConfig
import com.ongero.app.ui.payment.PaymentDialog
import com.ongero.app.ui.payment.PlusPaymentViewModel
import com.ongero.app.ui.redesign.flow.RedesignHome
import com.ongero.app.ui.redesign.flow.RedesignInCall
import com.ongero.app.ui.redesign.flow.RedesignOnboarding
import com.ongero.app.ui.redesign.flow.RedesignBlockedPeople
import com.ongero.app.ui.redesign.flow.RedesignBoundaries
import com.ongero.app.ui.redesign.flow.RedesignHelp
import com.ongero.app.ui.redesign.flow.RedesignPlus
import com.ongero.app.ui.redesign.flow.RedesignSettings
import com.ongero.app.ui.redesign.flow.RedesignSharerFlow
import com.ongero.app.ui.redesign.screens.AfterCallScreen
import com.ongero.app.ui.redesign.screens.CallFeeling
import com.ongero.app.ui.redesign.screens.RecognitionCheckScreen as RedesignRecognitionCheckScreen
import com.ongero.app.ui.redesign.screens.ReportScreen
import com.ongero.app.ui.redesign.screens.SplashScreen as RedesignSplashScreen
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.ongero.app.ui.screens.call.CallInfo
import com.ongero.app.ui.screens.call.InCallScreen
import com.ongero.app.ui.screens.feedback.PostCallFeedbackScreen
import com.ongero.app.ui.screens.feedback.RecognitionCheckScreen
import com.ongero.app.ui.screens.feedback.ReportProblemScreen
import com.ongero.app.ui.screens.help.HelpResourcesScreen
import com.ongero.app.ui.screens.home.HomeScreen
import com.ongero.app.ui.screens.listener.SetBoundariesScreen
import com.ongero.app.ui.screens.onboarding.OnboardingScreen
import com.ongero.app.ui.screens.settings.BlockedUsersScreen
import com.ongero.app.ui.screens.settings.SettingsScreen
import com.ongero.app.ui.screens.settings.SubscriptionPlan
import com.ongero.app.ui.screens.settings.SubscriptionScreen
import com.ongero.app.ui.screens.sharer.SharerFlowScreen
import com.ongero.app.ui.screens.sharer.Topic
import com.ongero.app.ui.screens.splash.SplashScreen

private const val TAG = "OngeroNavGraph"

/**
 * Main Navigation Graph for Ongero app
 */
@Composable
fun OngeroNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    val context = LocalContext.current
    val preferencesManager = PreferencesManager(context)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ============================================
        // SPLASH & ONBOARDING
        // ============================================

        composable(Screen.Splash.route) {
            if (BuildConfig.REDESIGN_UI) {
                val go: () -> Unit = {
                    val destination = if (preferencesManager.isOnboardingCompleted) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    go()
                }
                RedesignTheme { RedesignSplashScreen() }
                return@composable
            }
            SplashScreen(
                onSplashComplete = {
                    val destination = if (preferencesManager.isOnboardingCompleted) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            val complete: () -> Unit = {
                preferencesManager.isOnboardingCompleted = true
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            }
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme { RedesignOnboarding(onComplete = complete) }
            } else {
                OnboardingScreen(onOnboardingComplete = complete)
            }
        }

        // ============================================
        // HOME
        // ============================================

        composable(Screen.Home.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignHome(
                        onStartCall = { navController.navigate(Screen.SharerTopic.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onHelp = { navController.navigate(Screen.Help.route) },
                        onCallStarted = { callId ->
                            navController.navigate(Screen.InCall.createRoute(callId)) {
                                popUpTo(Screen.Home.route)
                            }
                        },
                    )
                }
                return@composable
            }
            HomeScreen(
                onStartCall = {
                    navController.navigate(Screen.SharerTopic.route)
                },
                onSetBoundaries = {
                    navController.navigate(Screen.Boundaries.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onHelpClick = {
                    navController.navigate(Screen.Help.route)
                },
                onCallStarted = { callId ->
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    Log.d(TAG, "🚀 HomeScreen.onCallStarted CALLBACK INVOKED")
                    Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
                    Log.d(TAG, "   callId: $callId")
                    Log.d(TAG, "   Current route: ${navController.currentDestination?.route}")
                    Log.d(TAG, "   Navigating to: ${Screen.InCall.createRoute(callId)}")
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    try {
                        navController.navigate(Screen.InCall.createRoute(callId)) {
                            popUpTo(Screen.Home.route)
                        }
                        Log.d(TAG, "   ✓ Navigation call completed")
                        Log.d(TAG, "   New route: ${navController.currentDestination?.route}")
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ NAVIGATION FAILED: ${e.message}", e)
                    }
                }
            )
        }

        // ============================================
        // SHARER FLOW
        // ============================================

        composable(Screen.SharerTopic.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignSharerFlow(
                        onBack = { navController.popBackStack() },
                        onCallStarted = { callId ->
                            navController.navigate(Screen.InCall.createRoute(callId)) {
                                popUpTo(Screen.Home.route)
                            }
                        },
                    )
                }
                return@composable
            }
            SharerFlowScreen(
                onBack = { navController.popBackStack() },
                onCallStarted = { callId ->
                    Log.d(TAG, "🚀 SharerFlowScreen.onCallStarted called with callId=$callId - navigating to InCall")
                    navController.navigate(Screen.InCall.createRoute(callId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // ============================================
        // LISTENER FLOW
        // ============================================

        composable(Screen.ListenerBoundaries.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignBoundaries(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                return@composable
            }
            SetBoundariesScreen(
                onBackClick = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable(Screen.Boundaries.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignBoundaries(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                return@composable
            }
            SetBoundariesScreen(
                onBackClick = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        // ============================================
        // IN-CALL
        // ============================================

        composable(
            route = Screen.InCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            Log.d(TAG, "📞 InCallScreen composable entered with callId=$callId")

            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme(night = true) {
                    RedesignInCall(
                        onEndCall = { elapsedSeconds ->
                            navController.navigate(
                                Screen.PostCallFeedback.createRoute(callId, elapsedSeconds)
                            ) { popUpTo(Screen.Home.route) }
                        },
                        onEmergencyExit = {
                            navController.navigate(Screen.ReportProblem.createRoute(callId)) {
                                popUpTo(Screen.Home.route)
                            }
                        },
                    )
                }
                return@composable
            }

            // Get call info from CallManager
            val currentRole by CallManager.currentRole.collectAsState()
            val callDuration by CallManager.currentCallDuration.collectAsState()
            val callTopic by CallManager.currentCallTopic.collectAsState()
            val callIntent by CallManager.currentCallIntent.collectAsState()
            val matchInfo by CallManager.matchInfo.collectAsState()

            Log.d(TAG, "📞 InCallScreen: role=$currentRole, duration=$callDuration, topic=$callTopic")

            // Determine topic enum from string
            val topic = try {
                Topic.valueOf(callTopic?.uppercase()?.replace(" ", "_") ?: "JUST_TALKING")
            } catch (e: Exception) {
                Topic.JUST_TALKING
            }

            InCallScreen(
                callInfo = CallInfo(
                    callId = callId,
                    participantId = matchInfo?.callId?.takeLast(4) ?: "0000",
                    topic = topic,
                    intent = callIntent ?: "Just need someone to talk to",
                    durationMinutes = callDuration,
                    isListener = currentRole == "listener"
                ),
                onEndCall = { elapsedSeconds ->
                    navController.navigate(Screen.PostCallFeedback.createRoute(callId, elapsedSeconds)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onEmergencyExit = { elapsedSeconds ->
                    // CRITICAL: End the call first before navigating
                    CallManager.endCall()
                    navController.navigate(Screen.ReportProblem.createRoute(callId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // ============================================
        // POST-CALL FEEDBACK
        // ============================================

        composable(
            route = Screen.PostCallFeedback.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("durationSeconds") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val durationSeconds = backStackEntry.arguments?.getInt("durationSeconds") ?: 0

            if (BuildConfig.REDESIGN_UI) {
                val home: () -> Unit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
                RedesignTheme {
                    AfterCallScreen(
                        onAnswer = { feeling ->
                            if (feeling == CallFeeling.WRONG) {
                                navController.navigate(Screen.ReportProblem.createRoute(callId))
                            } else {
                                home()
                            }
                        },
                        onSkip = home,
                    )
                }
                return@composable
            }

            PostCallFeedbackScreen(
                callDurationSeconds = durationSeconds,
                onSendThanks = {
                    // Send thanks via API
                },
                onReportProblem = {
                    navController.navigate(Screen.ReportProblem.createRoute(callId))
                },
                onDone = { sentiment ->
                    // Optionally show recognition check
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RecognitionCheck.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignRecognitionCheckScreen(
                        onAnswer = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                    )
                }
                return@composable
            }
            RecognitionCheckScreen(
                onResponse = { response ->
                    // Log response for analytics
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.ReportProblem.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""

            if (BuildConfig.REDESIGN_UI) {
                val home: () -> Unit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
                RedesignTheme {
                    ReportScreen(
                        onBack = home,
                        onSubmit = { reportData ->
                            CallManager.submitReport(
                                callId = callId,
                                reason = reportData.reason.name,
                                details = reportData.details,
                                blockUser = reportData.blockUser,
                            )
                            home()
                        },
                    )
                }
                return@composable
            }

            ReportProblemScreen(
                onBack = {
                    // Navigate to home since the call has already ended
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onSubmit = { reportData ->
                    // Submit report via WebSocket signaling
                    CallManager.submitReport(
                        callId = callId,
                        reason = reportData.reason.name,
                        details = reportData.details,
                        blockUser = reportData.blockUser
                    )

                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // ============================================
        // SETTINGS
        // ============================================

        composable(Screen.Settings.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignSettings(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                        onBoundaries = { navController.navigate(Screen.Boundaries.route) },
                        onBlockedUsers = { navController.navigate(Screen.BlockedUsers.route) },
                        onSubscription = { navController.navigate(Screen.Subscription.route) },
                    )
                }
                return@composable
            }
            SettingsScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onNavigateToBoundaries = {
                    navController.navigate(Screen.Boundaries.route)
                },
                onNavigateToBlockedUsers = {
                    navController.navigate(Screen.BlockedUsers.route)
                },
                onNavigateToSubscription = {
                    navController.navigate(Screen.Subscription.route)
                }
            )
        }

        composable(Screen.BlockedUsers.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    RedesignBlockedPeople(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                    )
                }
                return@composable
            }
            BlockedUsersScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Subscription.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme {
                    // RedesignPlus owns its own PlusPaymentViewModel: paying takes
                    // over the whole screen there, so the state belongs to it.
                    RedesignPlus(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                    )
                }
                return@composable
            }

            // The original screen has no room for a payment flow, so the same
            // ViewModel drives a dialog over the top of it. Scoped to this nav entry,
            // so it survives rotation but not leaving the screen.
            val paymentViewModel: PlusPaymentViewModel = viewModel(
                factory = PlusPaymentViewModel.factory(LocalContext.current),
            )
            val payment by paymentViewModel.state.collectAsState()
            var chosenPlan by remember { mutableStateOf(SubscriptionPlan.MONTHLY) }

            SubscriptionScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onSubscribe = { plan ->
                    chosenPlan = plan
                    paymentViewModel.begin()
                }
            )
            PaymentDialog(
                state = payment,
                onPay = { number -> paymentViewModel.pay(chosenPlan, number) },
                onDismiss = { paymentViewModel.cancel() },
            )
        }

        // ============================================
        // HELP
        // ============================================

        composable(Screen.Help.route) {
            if (BuildConfig.REDESIGN_UI) {
                RedesignTheme { RedesignHelp(onBack = { navController.popBackStack() }) }
                return@composable
            }
            HelpResourcesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

