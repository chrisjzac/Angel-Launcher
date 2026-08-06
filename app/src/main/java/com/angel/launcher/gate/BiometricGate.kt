package com.angel.launcher.gate

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.angel.launcher.ui.Metrics
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import java.util.concurrent.Executor

/**
 * The prototype's hold-to-fill ring simulated a sensor the web cannot reach.
 * On device the system prompt does the work — but the indicator keeps the
 * sensor's screen position so the unlocked layout still reads correctly.
 */
@Composable
fun BiometricGate(
    title: String,
    blurb: String,
    accent: Color,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var warning by remember { mutableStateOf<String?>(null) }
    val latestOnUnlocked by rememberUpdatedState(onUnlocked)

    fun authenticate() {
        val host = activity ?: return
        val manager = BiometricManager.from(host)
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val strongReady = manager.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (strongReady) {
                    setAllowedAuthenticators(strong)
                    setNegativeButtonText("Cancel")
                } else {
                    setAllowedAuthenticators(strong or credential)
                }
            }
            .setConfirmationRequired(false)
            .build()

        val executor = Executor { it.run() }
        val prompt = BiometricPrompt(
            host,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    warning = null
                    latestOnUnlocked()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    warning = message.toString()
                }
            },
        )
        runCatching { prompt.authenticate(info) }.onFailure { warning = it.message }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = height * Metrics.GATE_COPY_Y_FRACTION - 40.dp)
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = blurb,
                style = Type.Prose,
                color = Palette.Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 215.dp),
            )
            warning?.let {
                Text(
                    text = it,
                    style = Type.body(12.5),
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = height * Metrics.SENSOR_Y_FRACTION - (Metrics.SENSOR_INDICATOR_DP / 2).dp)
                .size(Metrics.SENSOR_INDICATOR_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(Metrics.SENSOR_INDICATOR_DP.dp)
                    .border(1.5.dp, Palette.Ink.copy(alpha = 0.16f), CircleShape)
                    .clickable { authenticate() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = "Unlock $title",
                    tint = accent,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

fun Context.findActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
