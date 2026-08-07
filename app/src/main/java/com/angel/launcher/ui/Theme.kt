package com.angel.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/* ---------------- fixed palette ---------------- */

object Palette {
    val Ground = Color(0xFF14161F)
    val Ink = Color(0xFFEDEBE6)
    val Dim = Color(0xFF7F859B)
    val Up = Color(0xFF7FD6A8)      // gains, credits
    val Down = Color(0xFFE0785C)    // losses, parser failures

    /** Surfaces are ink at low alpha, never a separate grey. */
    val Fill = Ink.copy(alpha = 0.05f)
    val FillHover = Ink.copy(alpha = 0.09f)
    val FillActive = Ink.copy(alpha = 0.10f)
    val Hairline = Ink.copy(alpha = 0.08f)
    val HairlineLoud = Ink.copy(alpha = 0.16f)
}

/* ---------------- weather-driven wallpaper ---------------- */

/**
 * Accent is NOT a constant — it travels with the sky. Every accented element
 * (rail active letter, sparkline, torch-on tint, biometric ring) reads from here.
 */
data class Sky(
    val key: String,
    val label: String,
    val accent: Color,
    private val warmGlow: Color,
    private val coolGlow: Color,
    private val base: List<Color>,
) {
    /** Layer these back-to-front; crossfade between skies over 900ms. */
    fun wallpaper(w: Float, h: Float): List<Brush> = listOf(
        Brush.linearGradient(base, start = Offset(w * 0.15f, 0f), end = Offset(w * 0.85f, h)),
        Brush.radialGradient(listOf(coolGlow, Color.Transparent), Offset(w * 0.12f, h * 0.06f), h * 0.55f),
        Brush.radialGradient(listOf(warmGlow, Color.Transparent), Offset(w * 0.82f, h * 0.88f), h * 0.45f),
    )

    companion object {
        val Sunny = Sky("sunny", "Clear", Color(0xFFFFB457),
            Color(0x42FF963A), Color(0x6B98461C),
            listOf(Color(0xFF26190F), Color(0xFF180F09), Color(0xFF0D0805)))

        val Clouds = Sky("clouds", "Overcast", Color(0xFFA9B6D0),
            Color(0x21AAB4C8), Color(0x5C586278),
            listOf(Color(0xFF1D2028), Color(0xFF15171D), Color(0xFF101116)))

        val Rain = Sky("rain", "Rain", Color(0xFFF2A65A),
            Color(0x33F2A65A), Color(0x6B3B4B7A),
            listOf(Color(0xFF1A1E2C), Color(0xFF12141C), Color(0xFF0F1117)))

        val Storm = Sky("storm", "Storms", Color(0xFFB79CFF),
            Color(0x389678E6), Color(0x80342E60),
            listOf(Color(0xFF191627), Color(0xFF12111C), Color(0xFF0C0B12)))

        val Snow = Sky("snow", "Snow", Color(0xFFA8D0F0),
            Color(0x2EC6D8EC), Color(0x5C5C84B2),
            listOf(Color(0xFF1B2230), Color(0xFF141A24), Color(0xFF0F1319)))

        val Fog = Sky("fog", "Fog", Color(0xFFCFC8B8),
            Color(0x1FC4C0B6), Color(0x526C707A),
            listOf(Color(0xFF202227), Color(0xFF17181C), Color(0xFF121316)))

        val all = listOf(Sunny, Clouds, Rain, Storm, Snow, Fog)

        fun byKey(key: String?): Sky? = all.firstOrNull { it.key == key }

        /** WMO weather_code from Open-Meteo. */
        fun fromWmoCode(code: Int): Sky = when {
            code == 0 || code == 1 -> Sunny
            code == 2 || code == 3 -> Clouds
            code == 45 || code == 48 -> Fog
            code >= 95 -> Storm
            code in 71..77 || code == 85 || code == 86 -> Snow
            else -> Rain
        }
    }
}

/* ---------------- tuned constants ---------------- */

object Metrics {
    const val RAIL_WIDTH_DP = 46
    const val RAIL_FISHEYE_FALLOFF_PX = 30f   // gaussian sigma
    const val RAIL_MAX_SCALE = 2.05f          // 1 + 1.05
    const val RAIL_MAX_SHIFT_DP = -20

    const val SWIPE_AXIS_SLOP_DP = 8
    const val SWIPE_COMMIT_DP = 70            // or 0.2 * width, whichever is smaller
    const val QUICK_ACTION_TRIP_DP = 28

    /**
     * SPEC 5 puts the quick action 56dp in from the right edge, hard against
     * the foot. On device that sits further out and lower than a thumb
     * naturally falls, so it is pulled in and lifted.
     */
    const val QUICK_ACTION_NUDGE_X_DP = -16
    const val QUICK_ACTION_NUDGE_Y_DP = -24

    /**
     * Fraction of display height. SPEC 7 derives 0.80 from the Galaxy S23's
     * ultrasonic reader; measured against the real handset the indicator sat
     * below the sensor, so it is raised. One number to nudge per device.
     */
    const val SENSOR_Y_FRACTION = 0.76f
    const val SENSOR_INDICATOR_DP = 72
    const val GATE_COPY_Y_FRACTION = 0.32f

    const val SKY_CROSSFADE_MS = 900
    const val PAGE_SETTLE_MS = 340

    /** Launcher page. */
    const val SIDE_PADDING_DP = 26
}
