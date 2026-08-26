package com.angel.launcher.money

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Live half of SMS ingestion — READ_SMS backfills what already exists
 * (SmsIngestor); this is what keeps the ledger current afterwards.
 *
 * This is deliberately NOT a default-SMS-handler receiver: no SMS_DELIVER
 * action, no manifest priority, no abortBroadcast. It only ever reads what
 * every other SMS app on the device also gets to read, and never composes or
 * sends. A future default-handler upgrade is possible if some carrier ever
 * delivers to this receiver unreliably, but nothing so far has needed it.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // A long SMS arrives as several PDUs from the same sender at once;
        // stitch them back into one body before parsing.
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val timestampMillis = messages.first().timestampMillis

        val pendingResult = goAsync()
        val repo = WealthRepository.get(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repo.ingestSms(sender, body, timestampMillis) }
            pendingResult.finish()
        }
    }
}
