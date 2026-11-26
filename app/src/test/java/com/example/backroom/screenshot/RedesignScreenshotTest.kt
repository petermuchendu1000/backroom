package com.example.backroom.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.backroom.ui.redesign.screens.AfterCallScreen
import com.example.backroom.ui.redesign.screens.AskScreen
import com.example.backroom.ui.redesign.screens.HomeMode
import com.example.backroom.ui.redesign.screens.HomeScreen
import com.example.backroom.ui.redesign.screens.InCallScreen
import com.example.backroom.ui.redesign.screens.LeavingCallScreen
import com.example.backroom.ui.redesign.screens.MicPermissionScreen
import com.example.backroom.ui.redesign.screens.WaitingScreen
import com.example.backroom.ui.redesign.screens.WelcomeScreen
import com.example.backroom.ui.redesign.theme.RedesignTheme
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
}
