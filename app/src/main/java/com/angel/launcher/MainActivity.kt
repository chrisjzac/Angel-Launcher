package com.angel.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity

/**
 * One activity, no fragments of our own — FragmentActivity only because
 * BiometricPrompt hosts itself in one.
 */
class MainActivity : FragmentActivity() {

    /** Bumped when Home is pressed while we are already the foreground. */
    private val resetSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // A launcher is where back stops.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        Crash.install(applicationContext)

        // Read once and drop it, so the report shows exactly once and the
        // next open is a real launch attempt.
        val trace = Crash.pending(this)
        if (trace != null) Crash.clear(this)

        setContent {
            if (trace != null) CrashReport(trace) else AngelApp(resetSignal.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resetSignal.intValue += 1
    }
}
