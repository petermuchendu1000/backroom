package com.ongero.app.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ongero.app.ui.redesign.screens.AfterCallScreen
import com.ongero.app.ui.redesign.screens.AskScreen
import com.ongero.app.ui.redesign.screens.BlockedPeopleScreen
import com.ongero.app.ui.redesign.screens.BoundariesScreen
import com.ongero.app.ui.redesign.screens.HelpScreen
import com.ongero.app.ui.redesign.screens.IncomingPreviewSheet
import com.ongero.app.ui.payment.PlusPaymentState
import com.ongero.app.ui.redesign.screens.PlusScreen
import com.ongero.app.ui.redesign.screens.RecognitionCheckScreen as RedesignRecognitionCheckScreen
import com.ongero.app.ui.redesign.screens.ReportScreen
import com.ongero.app.ui.redesign.screens.SplashScreen as RedesignSplashScreen
import com.ongero.app.ui.redesign.screens.HomeMode
import com.ongero.app.ui.redesign.screens.HomeScreen
import com.ongero.app.ui.redesign.screens.InCallScreen
import com.ongero.app.ui.redesign.screens.LeavingCallScreen
import com.ongero.app.ui.redesign.screens.MicPermissionScreen
import com.ongero.app.ui.redesign.screens.WaitingScreen
import com.ongero.app.ui.redesign.screens.SettingsScreen
import com.ongero.app.ui.redesign.screens.WelcomeScreen
import com.ongero.app.ui.screens.help.OngeroFaq
import com.ongero.app.ui.screens.listener.MaxIntensity
import com.ongero.app.ui.screens.sharer.Topic
import com.ongero.app.ui.screens.help.KenyaCrisisResources
import com.ongero.app.ui.redesign.theme.RedesignTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each redesigned screen to a PNG, off-device.
 *
 * Excluded from the normal test run (see app/build.gradle.kts) — this is an authoring
 * tool, not a regression gate. Run with:
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots
 *
 * Images land in app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class RedesignScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, night: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { RedesignTheme(night = night) { content() } }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun welcome() = shot("01-welcome") { WelcomeScreen(onBegin = {}) }

    @Test
    fun microphone() = shot("02-microphone") { MicPermissionScreen(onAllow = {}, onSkip = {}) }

    @Test
    fun home() = shot("03-home") {
        HomeScreen(connected = true, onStart = {}, onSettings = {}, onHelp = {})
    }

    @Test
    fun homeListenOff() = shot("03b-home-listen-off") {
        HomeScreen(
            connected = true,
            onStart = {},
            onSettings = {},
            onHelp = {},
            mode = HomeMode.LISTEN,
            available = false,
        )
    }

    @Test
    fun homeListenOn() = shot("03c-home-listening") {
        HomeScreen(
            connected = true,
            onStart = {},
            onSettings = {},
            onHelp = {},
            mode = HomeMode.LISTEN,
            available = true,
        )
    }

    @Test
    fun ask() = shot("04-ask") { AskScreen(onBack = {}, onFindSomeone = {}) }

    @Test
    fun waiting() = shot("05-waiting") { WaitingScreen(onCancel = {}) }

    @Test
    fun inCall() = shot("06-incall", night = true) {
        InCallScreen(
            topic = "Letting out",
            elapsed = "6:12",
            onMute = {},
            onEnd = {},
            onFeelUnsafe = {},
        )
    }

    @Test
    fun leavingCall() = shot("07-leaving", night = true) {
        LeavingCallScreen(
            elapsed = "6:12",
            onLeaveAndReport = {},
            onLeaveQuietly = {},
            onStay = {},
        )
    }

    @Test
    fun afterCall() = shot("08-after") { AfterCallScreen(onAnswer = {}, onSkip = {}) }

    @Test
    fun settings() = shot("09-settings") {
        SettingsScreen(
            blockedCount = 2,
            previewsEnabled = true,
            tipsEnabled = false,
            subscriptionStatus = null,
            versionName = "Version 1.0.0",
            onBack = {},
            onBoundaries = {},
            onBlockedUsers = {},
            onSubscription = {},
            onPreviewsChanged = {},
            onTipsChanged = {},
            onExportData = {},
            onDeleteAccount = {},
        )
    }

    @Test
    fun splash() = shot("11-splash") { RedesignSplashScreen() }

    @Test
    fun incomingPreview() = shot("12-incoming") {
        IncomingPreviewSheet(
            theirWords = "Lost someone close, need to talk through the pain",
            topic = "Grief",
            tone = "Heavy",
            minutes = 15,
            secondsLeft = 21,
            totalSeconds = 30,
            onAccept = {},
            onSkip = {},
        )
    }

    @Test
    fun report() = shot("13-report") { ReportScreen(onBack = {}, onSubmit = {}) }

    @Test
    fun boundaries() = shot("14-boundaries") {
        BoundariesScreen(
            initialTopics = Topic.entries.toSet() - Topic.SOMETHING_HARD,
            initialIntensity = MaxIntensity.UP_TO_HEAVY,
            initialMaxMinutes = 15,
            onBack = {},
            onSave = { _, _, _ -> },
        )
    }

    @Test
    fun blockedPeople() = shot("15-blocked") {
        BlockedPeopleScreen(
            blocked = listOf("anon_8f2c41", "anon_1d9b07"),
            onBack = {},
            onUnblock = {},
        )
    }

    @Test
    fun plus() = shot("16-plus") { PlusScreen(activeTier = null, onBack = {}, onSubscribe = {}) }

    @Test
    fun plusPhone() = shot("16b-plus-phone") {
        PlusScreen(
            activeTier = null,
            onBack = {},
            onSubscribe = {},
            payment = PlusPaymentState.NeedsPhone(),
        )
    }

    @Test
    fun plusWaiting() = shot("16c-plus-waiting") {
        PlusScreen(
            activeTier = null,
            onBack = {},
            onSubscribe = {},
            payment = PlusPaymentState.Waiting(amountKes = 500),
        )
    }

    @Test
    fun plusPaid() = shot("16d-plus-paid") {
        PlusScreen(
            activeTier = null,
            onBack = {},
            onSubscribe = {},
            payment = PlusPaymentState.Succeeded(receipt = "LGR019G3J2"),
        )
    }

    @Test
    fun plusFailed() = shot("16e-plus-failed") {
        PlusScreen(
            activeTier = null,
            onBack = {},
            onSubscribe = {},
            payment = PlusPaymentState.Failed("The prompt was cancelled."),
        )
    }

    @Test
    fun recognitionCheck() = shot("17-recognition") { RedesignRecognitionCheckScreen(onAnswer = {}) }

    // Rendered with the real verified resource list, not a fixture: this screen is the
    // one whose content being right matters most.
    @Test
    fun help() = shot("10-help") {
        HelpScreen(
            crisis = KenyaCrisisResources.primaryResources,
            more = KenyaCrisisResources.specializedResources,
            faqs = OngeroFaq.allFaqs,
            onCall = {},
            onBack = {},
        )
    }
}
