package com.angel.launcher.launcher

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.angel.launcher.ui.Metrics
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

val LETTERS: List<Char> = ('A'..'Z').toList()

private const val NO_POINTER = Float.NEGATIVE_INFINITY

/**
 * The product. Drag it and the list filters; letters swell under the finger.
 * Owns its vertical drags outright — every change is consumed, so the pager
 * underneath never sees a gesture that started here.
 *
 * The fisheye is read in the draw phase, not composition: a drag moves pixels
 * without recomposing 26 letters.
 */
@Composable
fun AlphabetRail(
    active: Char?,
    hasApps: (Char) -> Boolean,
    accent: Color,
    calm: Boolean,
    onLetter: (Char?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railHeight = remember { mutableIntStateOf(0) }
    val pointerY = remember { mutableFloatStateOf(NO_POINTER) }
    val dragging = remember { mutableStateOf(false) }
    val latestOnLetter by rememberUpdatedState(onLetter)
    val latestActive by rememberUpdatedState(active)

    val shiftPx = with(LocalDensity.current) { Metrics.RAIL_MAX_SHIFT_DP.dp.toPx() }

    Column(
        modifier = modifier
            .width(Metrics.RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .onSizeChanged { railHeight.intValue = it.height }
            .semantics { contentDescription = "Alphabet rail" }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val at = latestActive?.let { LETTERS.indexOf(it) } ?: -1
                when (event.key) {
                    Key.DirectionDown, Key.DirectionRight -> {
                        latestOnLetter(LETTERS[(at + 1).coerceIn(0, LETTERS.lastIndex)]); true
                    }
                    Key.DirectionUp, Key.DirectionLeft -> {
                        latestOnLetter(LETTERS[(at - 1).coerceIn(0, LETTERS.lastIndex)]); true
                    }
                    Key.Escape -> { latestOnLetter(null); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                fun report(y: Float) {
                    val height = railHeight.intValue
                    if (height <= 0) return
                    val step = height.toFloat() / LETTERS.size
                    val index = floor(y / step).toInt().coerceIn(0, LETTERS.lastIndex)
                    pointerY.floatValue = y
                    latestOnLetter(LETTERS[index])
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging.value = true
                    report(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        report(change.position.y)
                    }
                    dragging.value = false
                    pointerY.floatValue = NO_POINTER
                }
            },
    ) {
        LETTERS.forEachIndexed { index, letter ->
            val has = hasApps(letter)
            val base = if (has) 0.85f else 0.45f
            val isActive = letter == active

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = Type.RailKey,
                    color = if (isActive) accent else Palette.Dim,
                    modifier = Modifier.graphicsLayer {
                        val height = railHeight.intValue
                        val y = pointerY.floatValue
                        val fish = if (calm || !dragging.value || y == NO_POINTER || height <= 0) {
                            0f
                        } else {
                            val step = height.toFloat() / LETTERS.size
                            val d = abs(step * (index + 0.5f) - y) / Metrics.RAIL_FISHEYE_FALLOFF_PX
                            exp(-(d * d))
                        }
                        val scale = 1f + 1.05f * fish
                        scaleX = scale
                        scaleY = scale
                        translationX = shiftPx * fish
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        alpha = if (isActive) 1f else (base + 0.6f * fish).coerceAtMost(1f)
                    },
                )
            }
        }
    }
}
