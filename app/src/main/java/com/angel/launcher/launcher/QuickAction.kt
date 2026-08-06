package com.angel.launcher.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.angel.launcher.ui.Metrics
import com.angel.launcher.ui.Palette

/** Torch needs no permission, but it does need the camera free. */
class Torch(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val flashCamera: String? by lazy {
        runCatching {
            manager?.cameraIdList?.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    fun set(on: Boolean): Boolean {
        val id = flashCamera ?: return false
        return runCatching {
            manager?.setTorchMode(id, on)
            true
        }.getOrDefault(false)
    }
}

/**
 * Bottom right. Tap dials, up opens the camera, down throws the torch.
 * The icon swaps under the finger before the gesture commits, and the button
 * follows the drag at 30%, clamped to ±10dp.
 */
@Composable
fun QuickAction(
    accent: Color,
    torchOn: Boolean,
    onTorch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val torch = remember(context) { Torch(context) }

    val trip = with(density) { Metrics.QUICK_ACTION_TRIP_DP.dp.toPx() }
    val swap = trip / 2f
    val clamp = with(density) { 10.dp.toPx() }

    val drag = remember { mutableFloatStateOf(0f) }
    var hint by remember { mutableStateOf<ImageVector?>(null) }
    val latestTorchOn by rememberUpdatedState(torchOn)
    val latestOnTorch by rememberUpdatedState(onTorch)

    val shown: ImageVector = hint ?: Icons.Outlined.Phone
    val tint = if (torchOn) accent else Palette.Ink
    val edge = if (torchOn) accent else Palette.Ink.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                translationY = (drag.floatValue * 0.3f).coerceIn(-clamp, clamp)
            }
            .background(Palette.Ink.copy(alpha = 0.06f), CircleShape)
            .border(1.dp, edge, CircleShape)
            .semantics { contentDescription = "Phone. Swipe up for the camera, down for the torch." }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var dy = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        dy = change.position.y - down.position.y
                        drag.floatValue = dy
                        hint = when {
                            dy < -swap -> Icons.Filled.PhotoCamera
                            dy > swap -> Icons.Filled.FlashlightOn
                            else -> null
                        }
                    }
                    when {
                        dy < -trip -> context.fire(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                        dy > trip -> {
                            val next = !latestTorchOn
                            if (torch.set(next)) latestOnTorch(next)
                        }
                        else -> context.fire(Intent(Intent.ACTION_DIAL))
                    }
                    drag.floatValue = 0f
                    hint = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = shown,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun Context.fire(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
