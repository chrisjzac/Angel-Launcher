package com.angel.launcher.money

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Secondary Wealth pane source. SMS (SmsReceiver + SmsIngestor) is the source
 * of truth since it is the only one that can backfill history; this exists
 * for fintech apps that post a payment notification without ever sending a
 * matching SMS. WealthRepository.ingest dedupes the two against each other.
 *
 * This reads the notification the app posts for a payment, so it works
 * without this app being any kind of default handler. It only ever sees
 * notifications that arrive while access is on, which is why connecting also
 * sweeps whatever is still showing in the shade.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Everything still in the shade, so granting access is not an empty
        // pane until the next payment lands.
        val standing = runCatching { activeNotifications }.getOrNull().orEmpty()
        val repo = WealthRepository.get(applicationContext)
        standing.forEach { sbn ->
            val text = financialText(sbn) ?: return@forEach
            scope.launch { repo.ingestNotification(text, sbn.postTime) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = financialText(sbn) ?: return
        val repo = WealthRepository.get(applicationContext)
        scope.launch { repo.ingestNotification(text, sbn.postTime) }
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
