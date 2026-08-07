package com.angel.launcher

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Bring-up scaffolding. A launcher that dies on open leaves no way to read the
 * stack trace without adb, so persist it and show it on the next launch.
 *
 * Deliberately uses no app font, colour token or theme — this screen has to
 * survive whatever killed the real one. Delete this file once the app is
 * stable on device.
 */
object Crash {

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(context).writeText(
                    """
                    ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

                    ${error.stackTraceToString()}
                    """.trimIndent(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? =
        file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

@Composable
fun CrashReport(trace: String) {
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12100E))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = trace,
                color = Color(0xFFE8E2D9),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
