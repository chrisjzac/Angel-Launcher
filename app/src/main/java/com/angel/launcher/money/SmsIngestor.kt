package com.angel.launcher.money

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time historical scan of the SMS inbox via the Telephony content
 * provider, run the first time READ_SMS is granted (see WealthViewModel).
 * SmsReceiver takes over from there for anything that arrives afterwards —
 * this never runs as a background poll.
 */
object SmsIngestor {
    suspend fun backfill(context: Context): Int = withContext(Dispatchers.IO) {
        val repo = WealthRepository.get(context)
        var ingested = 0
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: continue
                val timestamp = cursor.getLong(dateIndex)
                repo.ingestSms(sender, body, timestamp)
                ingested++
            }
        }
        ingested
    }
}
