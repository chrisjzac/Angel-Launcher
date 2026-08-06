package com.angel.launcher.money

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.angel.launcher.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Money pane's source of truth. READ_SMS is a restricted permission granted
 * essentially only to default SMS handlers — bank notifications carry the same
 * text and cost nothing at review time. SmsParser takes a String either way.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString(": ").trim()

        // Only things that quote an amount are worth keeping; the parser decides
        // the rest, and says out loud what it could not read.
        if (text.isBlank() || !CARRIES_AMOUNT.containsMatchIn(text)) return

        scope.launch { Prefs.addMessages(applicationContext, listOf(text)) }
    }

    companion object {
        private val CARRIES_AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*[\d,]""", RegexOption.IGNORE_CASE)

        fun granted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val me = ComponentName(context, PaymentNotificationListener::class.java)
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == me.packageName
            }
        }
    }
}
