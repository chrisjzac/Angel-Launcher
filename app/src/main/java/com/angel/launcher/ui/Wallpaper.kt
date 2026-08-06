package com.angel.launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Six palettes, crossfaded over 900ms. Nothing else on screen owns a background.
 */
@Composable
fun SkyWallpaper(sky: Sky, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = sky,
        animationSpec = tween(Metrics.SKY_CROSSFADE_MS),
        label = "sky",
        modifier = modifier.fillMaxSize(),
    ) { active ->
        Canvas(Modifier.fillMaxSize()) {
            active.wallpaper(size.width, size.height).forEach { brush -> drawRect(brush) }
        }
    }
    Grain()
}

/** A 3px dot grid at 5% ink — the prototype's paper texture. */
@Composable
private fun Grain() {
    val brush = remember {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        bitmap.setPixel(1, 1, android.graphics.Color.argb(13, 237, 235, 230))
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(Modifier.fillMaxSize()) { drawRect(brush) }
}
