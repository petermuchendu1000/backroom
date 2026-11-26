package com.example.backroom.ui.redesign.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Redesign theme — warm, low-contrast, generous.
 *
 * Deliberately separate from [com.example.backroom.ui.theme] so the redesign can be
 * adopted screen by screen without changing how any existing screen looks.
 *
 * The display face is [FontFamily.Serif] rather than a bundled font: the mockups use
 * Newsreader, and dropping Newsreader + Inter into res/font and swapping the two
 * families below is the only change needed to match them exactly.
 */

// ── Light ──────────────────────────────────────────────────────────
private val Paper = Color(0xFFF7F4F0)
private val Surface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF221F2E)
private val Muted = Color(0xFF6E6881)
private val Line = Color(0xFFE8E2DB)
private val Violet = Color(0xFF5E54A4)
private val VioletSoft = Color(0xFFEBE7F8)
private val VioletSofter = Color(0xFFEFEBFA)
private val VioletMid = Color(0xFFE5DFF6)
private val VioletDeep = Color(0xFFCFC5EE)
private val Danger = Color(0xFFC4503F)
private val Calm = Color(0xFF3F9E8C)

// ── Dark (the call surfaces) ───────────────────────────────────────
private val NightPaper = Color(0xFF15121E)
private val NightSurface = Color(0xFF211C2F)
private val NightInk = Color(0xFFF2EFF8)
private val NightMuted = Color(0xFF8B82A8)
private val NightLine = Color(0xFF332B4D)
private val NightRing1 = Color(0xFF241E38)
private val NightRing2 = Color(0xFF2F2749)

/** Tokens the screens read directly, so no screen hard-codes a hex. */
@Immutable
data class BackroomColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentSofter: Color,
    val accentMid: Color,
    val accentDeep: Color,
    val danger: Color,
    val calm: Color,
    val onAccent: Color,
)

val LightColors = BackroomColors(
    paper = Paper,
    surface = Surface,
    ink = Ink,
    muted = Muted,
    line = Line,
    accent = Violet,
    accentSoft = VioletSoft,
    accentSofter = VioletSofter,
    accentMid = VioletMid,
    accentDeep = VioletDeep,
    danger = Danger,
    calm = Calm,
    onAccent = Color.White,
)

val NightColors = BackroomColors(
    paper = NightPaper,
    surface = NightSurface,
    ink = NightInk,
    muted = NightMuted,
    line = NightLine,
    accent = Violet,
    accentSoft = NightRing2,
    accentSofter = NightRing1,
    accentMid = NightRing2,
    accentDeep = Violet,
    danger = Danger,
    calm = Calm,
    onAccent = Color.White,
)

val LocalBackroomColors = staticCompositionLocalOf { LightColors }

/** The one place a screen gets its colours. */
val backroomColors: BackroomColors
    @Composable get() = LocalBackroomColors.current

private val Display = FontFamily.Serif
private val Body = FontFamily.Default

val BackroomTypography = Typography(
    // Display — the single large line a screen is allowed
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 40.sp, lineHeight = 46.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 34.sp, lineHeight = 41.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 31.sp, lineHeight = 39.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 29.sp, lineHeight = 38.sp,
    ),
    // Body / UI
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(
        fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp),
)

@Composable
fun RedesignTheme(
    night: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (night) NightColors else LightColors
    val scheme = if (night) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(LocalBackroomColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = BackroomTypography, content = content)
    }
}
