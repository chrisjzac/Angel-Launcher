package com.angel.launcher.money

import android.content.Context
import com.angel.launcher.money.db.AccountEntity
import com.angel.launcher.money.db.CategoryRuleEntity
import com.angel.launcher.money.db.TransactionEntity
import com.angel.launcher.money.db.TxnDirection
import com.angel.launcher.money.db.WealthDatabase
import java.security.MessageDigest

/**
 * Turns a parsed SMS or notification into a stored transaction. This is the
 * only place either source touches the database, which is what makes the
 * cross-source dedupe in [ingest] correct — both SmsIngestor/SmsReceiver and
 * PaymentNotificationListener call through here rather than writing rows
 * themselves.
 */
class WealthRepository private constructor(context: Context) {

    private val db = WealthDatabase.get(context)
    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val categoryRuleDao = db.categoryRuleDao()

    suspend fun ingestSms(sender: String, body: String, timestampMillis: Long) =
        ingest(body, timestampMillis, smsSender = sender)

    suspend fun ingestNotification(body: String, timestampMillis: Long) =
        ingest(body, timestampMillis, smsSender = null)

    private suspend fun ingest(body: String, timestampMillis: Long, smsSender: String?) {
        val parsed = (SmsParser.parse(body, smsSender) as? ParseOutcome.Parsed)?.txn ?: return

        val accountId = AccountEntity.idFor(parsed.issuer, parsed.last4)
        if (accountDao.get(accountId) == null) {
            accountDao.insert(
                AccountEntity(
                    id = accountId,
                    issuer = parsed.issuer,
                    last4 = parsed.last4,
                    type = parsed.accountType,
                    displayName = if (parsed.last4 != null) "${parsed.issuer} ··${parsed.last4}" else parsed.issuer,
                ),
            )
        }

        val category = Categorizer.categorize(
            parsed.merchantDisplay, parsed.direction, parsed.mode, categoryRuleDao.getAll(),
        )

        val existing = transactionDao.findFuzzyMatch(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction,
            accountId = accountId,
            centerMillis = timestampMillis,
            fromMillis = timestampMillis - DEDUPE_WINDOW_MILLIS,
            toMillis = timestampMillis + DEDUPE_WINDOW_MILLIS,
        )

        if (existing != null) {
            // SMS is the source of truth: a real SMS landing for a standing
            // notification-derived row upgrades it in place instead of
            // duplicating it. A notification landing for a row that already
            // has SMS behind it is just the bank app's own duplicate — drop it.
            if (smsSender != null && existing.smsSender == null) {
                transactionDao.promoteToSms(
                    id = existing.id,
                    rawSmsBody = body,
                    smsSender = smsSender,
                    dedupeHash = dedupeHash(smsSender, body),
                    balanceAfterPaise = parsed.balanceAfterPaise,
                )
            }
            return
        }

        transactionDao.insert(
            TransactionEntity(
                timestampMillis = timestampMillis,
                amountPaise = parsed.amountPaise,
                direction = parsed.direction,
                accountId = accountId,
                merchantRaw = parsed.merchantRaw,
                merchantDisplay = parsed.merchantDisplay,
                category = category,
                mode = parsed.mode,
                balanceAfterPaise = parsed.balanceAfterPaise,
                rawSmsBody = body,
                smsSender = smsSender,
                dedupeHash = if (smsSender != null) {
                    dedupeHash(smsSender, body)
                } else {
                    dedupeHash("notif", accountId, parsed.amountPaise.toString(), parsed.direction.name, timestampMillis.toString())
                },
            ),
        )
    }

    /** Long-press recategorize: this row now, this merchant from now on. */
    suspend fun recategorize(transactionId: Long, merchantDisplay: String, category: String) {
        transactionDao.setCategory(transactionId, category)
        categoryRuleDao.upsert(
            CategoryRuleEntity(matchPattern = merchantDisplay.lowercase(), category = category, userCreated = true),
        )
    }

    fun pagingSource(direction: TxnDirection) = transactionDao.pagingSource(direction)
    fun summary(direction: TxnDirection) = transactionDao.summary(direction)
    fun accountBreakdown(direction: TxnDirection) = transactionDao.accountBreakdown(direction)
    fun accounts() = accountDao.observeAll()

    suspend fun periodTotal(direction: TxnDirection, fromMillis: Long, toMillis: Long) =
        transactionDao.periodTotal(direction, fromMillis, toMillis)

    suspend fun hasAnyTransactions() = transactionDao.count() > 0

    private fun dedupeHash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DEDUPE_WINDOW_MILLIS = 5 * 60 * 1000L

        @Volatile private var instance: WealthRepository? = null

        fun get(context: Context): WealthRepository =
            instance ?: synchronized(this) {
                instance ?: WealthRepository(context.applicationContext).also { instance = it }
            }
    }
}
