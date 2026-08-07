package com.angel.launcher.gate

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * The system prompt, with no screen of our own around it. Panes stay visible
 * and mask their figures instead of being replaced by a lock.
 */
@Composable
fun rememberUnlock(title: String, onUnlocked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val latest by rememberUpdatedState(onUnlocked)

    return remember(activity, title) {
        {
            val host = activity
            if (host == null) {
                latest()
            } else {
                val manager = BiometricManager.from(host)
                val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
                val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
                val strongReady = manager.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS
                val credentialReady =
                    manager.canAuthenticate(credential) == BiometricManager.BIOMETRIC_SUCCESS

                if (!strongReady && !credentialReady) {
                    // Nothing to authenticate against; a permanent lock would
                    // just be a pane the user can never read.
                    latest()
                } else {
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .apply {
                            if (strongReady) {
                                setAllowedAuthenticators(strong)
                                setNegativeButtonText("Cancel")
                            } else {
                                setAllowedAuthenticators(credential)
                            }
                        }
                        .setConfirmationRequired(false)
                        .build()

                    val executor = Executor { it.run() }
                    val prompt = BiometricPrompt(
                        host,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult,
                            ) = latest()
                        },
                    )
                    runCatching { prompt.authenticate(info) }
                }
            }
            Unit
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
