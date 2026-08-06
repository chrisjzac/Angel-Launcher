package com.angel.launcher.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.angel.launcher.R

/**
 * Three typefaces, each with one job. Downloadable via Google Fonts — no bundled
 * TTFs. If the provider is unavailable Compose falls back to the platform face,
 * which is why nothing here depends on a glyph the fallback lacks.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val bodoni = GoogleFont("Bodoni Moda")
private val hanken = GoogleFont("Hanken Grotesk")
private val jetbrains = GoogleFont("JetBrains Mono")

/** Clock, ghost letter, money figures, temperatures. */
val Display = FontFamily(
    Font(bodoni, provider, FontWeight.Normal),
    Font(bodoni, provider, FontWeight.Medium),
    Font(bodoni, provider, FontWeight.SemiBold),
)

/** App names, device names, prose. */
val Body = FontFamily(
    Font(hanken, provider, FontWeight.Normal),
    Font(hanken, provider, FontWeight.Medium),
    Font(hanken, provider, FontWeight.SemiBold),
)

/** All uppercase micro-labels, tickers, metadata. */
val Utility = FontFamily(
    Font(jetbrains, provider, FontWeight.Medium),
    Font(jetbrains, provider, FontWeight.Bold),
)

object Type {
    val Clock = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 60.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.012).em,
    )

    val Date = TextStyle(
        fontFamily = Utility,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.2.em,
    )

    /** Weather pill and every other uppercase micro-label. */
    val Micro = TextStyle(
        fontFamily = Utility,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        letterSpacing = 0.16.em,
    )

    val MicroSmall = TextStyle(
        fontFamily = Utility,
        fontWeight = FontWeight.Medium,
        fontSize = 8.5.sp,
        letterSpacing = 0.18.em,
    )

    val Section = TextStyle(
        fontFamily = Utility,
        fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp,
        letterSpacing = 0.22.em,
    )

    val AppRow = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        letterSpacing = (-0.012).em,
    )

    val RailKey = TextStyle(
        fontFamily = Utility,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
    )

    val Ghost = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 265.sp,
        lineHeight = 265.sp,
    )

    val Prose = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.6.sp,
    )

    /** Money / thermostat figures. */
    fun figure(size: Int) = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = size.sp,
        lineHeight = (size * 1.02f).sp,
        letterSpacing = (-0.01).em,
    )

    fun mono(size: Double, weight: FontWeight = FontWeight.Medium) = TextStyle(
        fontFamily = Utility,
        fontWeight = weight,
        fontSize = size.sp,
    )

    fun body(size: Double, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = Body,
        fontWeight = weight,
        fontSize = size.sp,
    )
}
