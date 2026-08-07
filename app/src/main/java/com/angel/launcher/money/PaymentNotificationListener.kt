package com.angel.launcher.money

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.angel.launcher.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Money pane's source of truth. READ_SMS is a restricted permission granted
 * essentially only to default SMS handlers — bank notifications carry the same
 * text and cost nothing at review time. SmsParser takes a String either way.
 *
 * This reads the notification the messaging app posts for an incoming SMS, so
 * it works without this app being the SMS handler. It only ever sees messages
 * that arrive while notification access is on, which is why connecting also
 * sweeps whatever is still showing in the shade.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Everything still in the shade, so granting access is not an empty
        // pane until the next payment lands.
        val standing = runCatching { activeNotifications }.getOrNull().orEmpty()
            .mapNotNull { financialText(it) }
        if (standing.isNotEmpty()) {
            scope.launch { Prefs.addMessages(applicationContext, standing) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = financialText(sbn) ?: return
        scope.launch { Prefs.addMessages(applicationContext, listOf(text)) }
    }

    /** Title and body joined, or null when it quotes no amount. */
    private fun financialText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString(": ").trim()

        // Only things quoting an amount are worth keeping; the parser decides
        // the rest, and says out loud what it could not read.
        return text.takeIf { it.isNotBlank() && CARRIES_AMOUNT.containsMatchIn(it) }
    }

    companion object {
        private val CARRIES_AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*[\d,]""", RegexOption.IGNORE_CASE)

        fun granted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
