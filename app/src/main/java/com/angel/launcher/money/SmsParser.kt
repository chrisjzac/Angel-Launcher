package com.angel.launcher.money

import com.angel.launcher.money.db.AccountType
import com.angel.launcher.money.db.TxnDirection
import com.angel.launcher.money.db.TxnMode
import java.math.BigDecimal

/**
 * Rule-based parser for Indian bank / card / UPI-app transaction messages.
 *
 * Source-agnostic: takes a String, so it works over a NotificationListenerService
 * payload just as well as an SMS body — see WealthRepository, which is the only
 * place either source is turned into a TransactionEntity. The message's own
 * timestamp is never parsed out and trusted; the caller supplies the real
 * SMS-received / notification-posted time, which is precise where a "05-Aug-26"
 * date field in the text is not (no time of day, ambiguous two-digit years).
 *
 * Two layers of rule, both independently extensible:
 *  - ISSUERS recognises which bank or UPI app sent the message and whether the
 *    underlying instrument is a bank account or a credit card. Adding a bank
 *    that already fits the shared amount/verb/balance wording below is just
 *    one new line here.
 *  - MERCHANT_RULES is an ordered list of named-capture-group patterns tried
 *    in turn to find the counterparty. This is where wording actually differs
 *    enough to need its own rule — a structured UPI info field, a VPA handle,
 *    "paid to X via <app>", or the generic "at/to/from/by/towards X" a plain
 *    bank template uses. A new *format* is a new entry here; a new bank using
 *    a format already covered needs nothing added.
 * Amount, account-last-4 and balance are one pattern each shared by every
 * issuer, because that part of the wording barely varies bank to bank.
 */
object SmsParser {

    // ---- shared fragments ----

    private val DEBIT_VERB = Regex(
        """\b(debited|spent|withdrawn|debit|paid|sent|charged|used for a?n? ?payment)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CREDIT_VERB = Regex(
        """\b(credited|received|refunded|refund|deposited)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AMOUNT = Regex(
        """(?:rs\.?|inr|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val LAST4_MASK = Regex(
        """(?:a/?c(?:count|t)?\.?\s*(?:no\.?)?|card(?:\s*(?:no\.?|number))?)\s*[xX×*]{1,4}(?<last4>\d{3,4})""",
        RegexOption.IGNORE_CASE,
    )
    private val LAST4_ENDING = Regex(
        """ending\s*(?:in|with)?\s*(?<last4>\d{3,4})""",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE = Regex(
        """(?:avl\.?\s*bal(?:ance)?|available balance|closing balance)\.?:?\s*(?:rs\.?|inr|₹)?\s*(?<balance>[\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // ---- rejection triggers, checked before treating anything as a transaction ----

    private val OTP_HINT = Regex("""\botp\b|one[- ]time password""", RegexOption.IGNORE_CASE)
    private val FAILED_HINT = Regex(
        """\b(declined|failed|unsuccessful|not successful|could not be completed)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE_ENQUIRY_HINT = Regex(
        """available balance is|your balance is|balance enquiry""",
        RegexOption.IGNORE_CASE,
    )
    private val DUE_REMINDER_HINT = Regex(
        """\bis due\b|\bdue on\b|\bdue by\b|\bwill be due\b|\bdue date\b|kindly pay|please pay by""",
        RegexOption.IGNORE_CASE,
    )
    private val PROMO_HINT = Regex(
        """%\s*off|\boff on\b|use code|t&c apply|limited period|click here|download now|offer valid|win exciting""",
        RegexOption.IGNORE_CASE,
    )

    // ---- issuer recognition ----

    private data class Issuer(val name: String, val hint: Regex, val forceCreditCard: Boolean = false)

    private val ISSUERS = listOf(
        Issuer("HDFC Bank", Regex("""\bHDFC\b""", RegexOption.IGNORE_CASE)),
        Issuer("ICICI Bank", Regex("""\bICICI\b""", RegexOption.IGNORE_CASE)),
        Issuer("State Bank of India", Regex("""\bSBI\b|State Bank of India""", RegexOption.IGNORE_CASE)),
        Issuer("Axis Bank", Regex("""\bAxis Bank\b""", RegexOption.IGNORE_CASE)),
        Issuer("Kotak Mahindra Bank", Regex("""\bKotak\b""", RegexOption.IGNORE_CASE)),
        Issuer("IDFC FIRST Bank", Regex("""\bIDFC\s*FIRST\b|\bIDFC\b""", RegexOption.IGNORE_CASE)),
        Issuer("Yes Bank", Regex("""\bYes Bank\b""", RegexOption.IGNORE_CASE)),
        Issuer("IndusInd Bank", Regex("""\bIndusInd\b""", RegexOption.IGNORE_CASE)),
        Issuer("RBL Bank", Regex("""\bRBL Bank\b""", RegexOption.IGNORE_CASE)),
        Issuer("Federal Bank", Regex("""\bFederal Bank\b""", RegexOption.IGNORE_CASE)),
        Issuer(
            "American Express",
            Regex("""\bAmerican Express\b|\bAmex\b""", RegexOption.IGNORE_CASE),
            forceCreditCard = true,
        ),
        Issuer("Google Pay", Regex("""\bGoogle Pay\b|\bGPay\b""", RegexOption.IGNORE_CASE)),
        Issuer("PhonePe", Regex("""\bPhonePe\b""", RegexOption.IGNORE_CASE)),
        Issuer("Paytm", Regex("""\bPaytm\b""", RegexOption.IGNORE_CASE)),
    )

    private val ACCOUNT_TYPE_HINT = Regex(
        """credit card|card ending|card no\.?\s*[xX×*]|card\s*[xX×*]{2,}""",
        RegexOption.IGNORE_CASE,
    )

    private val MODES = listOf(
        TxnMode.UPI to Regex("""\bUPI\b|\bVPA\b""", RegexOption.IGNORE_CASE),
        TxnMode.ATM to Regex("""\bATM\b|cash withdrawal""", RegexOption.IGNORE_CASE),
        TxnMode.NEFT to Regex("""\bNEFT\b""", RegexOption.IGNORE_CASE),
        TxnMode.IMPS to Regex("""\bIMPS\b""", RegexOption.IGNORE_CASE),
        TxnMode.CARD to Regex("""\bcard\b""", RegexOption.IGNORE_CASE),
    )

    /** Named-capture merchant strategies, tried in order; first hit wins. */
    private val MERCHANT_RULES: List<Regex> = listOf(
        // Structured UPI info field: "Info: UPI/P2M/123456789012/payee name".
        Regex(
            """Info:?\s*UPI/(?:[A-Za-z0-9]+/)?\d+/(?<merchant>[\w .&'\-]+?)(?:[.,]|\s+Avl|\s+on\b|$)""",
            RegexOption.IGNORE_CASE,
        ),
        // A bare "UPI/vpa@bank" reference, no "Info:" field wrapped around it.
        Regex("""UPI/(?<merchant>[\w.\-]+@[\w.\-]+)""", RegexOption.IGNORE_CASE),
        // A UPI VPA handle: "VPA bluetokai@okhdfcbank", "to merchant@ybl", "from sender@okaxis".
        Regex("""(?:VPA\s+|to\s+|from\s+)(?<merchant>[\w.\-]+@[\w.\-]+)""", RegexOption.IGNORE_CASE),
        // GPay / PhonePe / Paytm app phrasing: "paid Rs.150 to X using Google Pay",
        // "Rs.500 received from X via PhonePe". The amount always sits between
        // the verb and the preposition, so this anchors on the trailing
        // "using/via <app>" instead of assuming "paid to" are adjacent words.
        Regex(
            """\b(?:to|from)\s+(?<merchant>[A-Za-z][A-Za-z0-9&.'\- ]{1,40}?)\s+""" +
                """(?:using|via|through|successfully)\b""",
            RegexOption.IGNORE_CASE,
        ),
        // Card swipe / generic "at MERCHANT".
        Regex("""\bat\s+(?<merchant>[A-Za-z][A-Za-z0-9&.\- ]{1,28}?)(?=\s*(?:[.,]|on\b|$))"""),
        // Generic "to MERCHANT" for a debit.
        Regex("""\bto\s+(?<merchant>[A-Za-z][A-Za-z0-9&.\- ]{1,28}?)(?=\s*(?:[.,]|on\b|$))"""),
        // Generic "from MERCHANT" / "by MERCHANT" for a credit.
        Regex("""\b(?:from|by)\s+(?<merchant>[A-Za-z][A-Za-z0-9&.\- ]{1,28}?)(?=\s*(?:[.,]|on\b|$))"""),
        Regex(
            """\btowards\s+(?<merchant>[A-Za-z0-9][A-Za-z0-9&.\- ]{1,28}?)(?=\s*(?:[.,]|on\b|$))""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val TRAILING_PUNCT = Regex("""[.,;:\-]+$""")

    fun parse(body: String, sender: String? = null): ParseOutcome {
        val text = body.trim()
        if (text.isBlank()) return ParseOutcome.Rejected("Empty message")

        if (OTP_HINT.containsMatchIn(text)) return ParseOutcome.Rejected("OTP message, not a transaction")
        if (FAILED_HINT.containsMatchIn(text)) return ParseOutcome.Rejected("Transaction failed or was declined")

        val isOut = DEBIT_VERB.containsMatchIn(text)
        val isIn = CREDIT_VERB.containsMatchIn(text)

        if (!isOut && !isIn) {
            if (BALANCE_ENQUIRY_HINT.containsMatchIn(text)) {
                return ParseOutcome.Rejected("Balance enquiry, not a transaction")
            }
            if (DUE_REMINDER_HINT.containsMatchIn(text)) {
                return ParseOutcome.Rejected("Payment reminder, not a completed transaction")
            }
        }
        if (PROMO_HINT.containsMatchIn(text)) return ParseOutcome.Rejected("Promotional message")

        val amountRaw = namedGroup(AMOUNT, text, "amount") ?: return ParseOutcome.Rejected("No amount found")
        if (!isOut && !isIn) return ParseOutcome.Rejected("No debit or credit verb")

        val amountPaise = toPaise(amountRaw) ?: return ParseOutcome.Rejected("Unreadable amount")
        val direction = if (isIn && !isOut) TxnDirection.CREDIT else TxnDirection.DEBIT

        val issuer = ISSUERS.firstOrNull { it.hint.containsMatchIn(text) }
        val accountType = when {
            issuer?.forceCreditCard == true -> AccountType.CREDIT_CARD
            ACCOUNT_TYPE_HINT.containsMatchIn(text) -> AccountType.CREDIT_CARD
            else -> AccountType.BANK
        }
        val last4 = namedGroup(LAST4_MASK, text, "last4") ?: namedGroup(LAST4_ENDING, text, "last4")

        val merchantRaw = MERCHANT_RULES.firstNotNullOfOrNull { rule -> namedGroup(rule, text, "merchant") }
            ?.trim()
            ?.replace(TRAILING_PUNCT, "")
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

        val mode = MODES.firstOrNull { it.second.containsMatchIn(text) }?.first ?: TxnMode.OTHER
        val balancePaise = namedGroup(BALANCE, text, "balance")?.let { toPaise(it) }

        return ParseOutcome.Parsed(
            ParsedTxn(
                amountPaise = amountPaise,
                direction = direction,
                issuer = issuer?.name ?: "Unknown",
                last4 = last4,
                accountType = accountType,
                merchantRaw = merchantRaw,
                merchantDisplay = humanize(merchantRaw),
                mode = mode,
                balanceAfterPaise = balancePaise,
            ),
        )
    }

    /**
     * Kotlin's MatchResult.groups is typed MatchGroupCollection, which has no
     * name-based accessor — only the JVM Matcher underneath does. Going
     * straight to it sidesteps that and keeps every extraction here reading
     * off the same named group the regex declares.
     */
    private fun namedGroup(pattern: Regex, text: String, name: String): String? {
        val matcher = pattern.toPattern().matcher(text)
        if (!matcher.find()) return null
        return runCatching { matcher.group(name) }.getOrNull()
    }

    private fun toPaise(numeric: String): Long? {
        val rupees = runCatching { BigDecimal(numeric.replace(",", "")) }.getOrNull() ?: return null
        return rupees.movePointRight(2).toLong()
    }

    /** VPA handles and ALL-CAPS merchant strings, turned into something readable. */
    private fun humanize(raw: String): String {
        val base = if ('@' in raw) raw.substringBefore('@') else raw
        return base.replace(Regex("""[._\-]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.length <= 3 && word == word.uppercase()) word
                else word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}

data class ParsedTxn(
    val amountPaise: Long,
    val direction: TxnDirection,
    val issuer: String,
    val last4: String?,
    val accountType: AccountType,
    val merchantRaw: String,
    val merchantDisplay: String,
    val mode: TxnMode,
    val balanceAfterPaise: Long?,
)

sealed interface ParseOutcome {
    data class Parsed(val txn: ParsedTxn) : ParseOutcome
    data class Rejected(val reason: String) : ParseOutcome
}
