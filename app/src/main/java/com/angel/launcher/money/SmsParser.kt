package com.angel.launcher.money

/**
 * Parses Indian bank / UPI transaction messages into structured transactions.
 *
 * Ported from the validated JS implementation in reference/launcher.jsx and
 * verified against reference/sms-fixtures.tsv — wire that file into a unit test
 * before touching these expressions.
 *
 * Source-agnostic: takes a String, so it works over a NotificationListenerService
 * payload just as well as an SMS body. Do NOT plumb this to READ_SMS — see CLAUDE.md.
 */

enum class Direction { OUT, IN }

data class Category(val key: String, val tint: Long)

data class Txn(
    val id: Int,
    val raw: String,
    val direction: Direction,
    val amount: Double,
    val merchant: String,
    val account: String?,
    val date: String?,
    val channel: String,
    val bank: String?,
    val category: Category,
)

sealed interface ParseResult {
    data class Parsed(val txn: Txn) : ParseResult
    data class Skipped(val id: Int, val raw: String, val why: String) : ParseResult
}

object SmsParser {

    private val OUT_VERB = Regex("""\b(debited|spent|withdrawn|debit|paid|sent)\b""", RegexOption.IGNORE_CASE)
    private val IN_VERB = Regex("""\b(credited|received|refunded|refund|deposited)\b""", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val ACCOUNT = Regex("""(?:a/c|acct|account|card)\s*(?:no\.?)?\s*[xX*]{2,}(\d{3,4})""", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""(\d{1,2})[-/ ]([A-Za-z]{3}|\d{1,2})[-/ ](\d{2,4})""")
    private val VPA = Regex("""VPA\s+([\w.\-]+@[\w.\-]+)""", RegexOption.IGNORE_CASE)
    private val AT = Regex("""\bat\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))""")
    private val TO = Regex("""\bto\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))""")
    private val FROM = Regex("""\bfrom\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))""")
    private val TOWARDS = Regex("""\btowards\s+([A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))""", RegexOption.IGNORE_CASE)
    private val BY = Regex("""\bby\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))""")
    private val CHANNEL = Regex("""\b(UPI|IMPS|NEFT|RTGS|ATM|Card)\b""", RegexOption.IGNORE_CASE)
    private val BANK = Regex("""\b(HDFC|ICICI|SBI|AXIS|KOTAK|IDFC|YES BANK)\b""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCT = Regex("""[.,;:\-]+$""")

    /** Order matters — first hit wins. */
    private val CATEGORIES = listOf(
        Category("Income", 0xFF7FD6A8) to listOf("salary", "payout", "interest", "dividend"),
        Category("Food", 0xFFF2A65A) to listOf("swiggy", "zomato", "tokai", "cafe", "restaurant", "dominos"),
        Category("Fuel", 0xFFE0785C) to listOf("indianoil", "hpcl", "bharat", "shell", "petrol"),
        Category("Shopping", 0xFF9DB4E8) to listOf("amazon", "flipkart", "myntra", "ajio"),
        Category("Subscriptions", 0xFFB79CFF) to listOf("netflix", "spotify", "prime", "youtube", "hotstar"),
        Category("Bills", 0xFF8FD0D6) to listOf("bescom", "airtel", "jio", "electric", "gas", "broadband"),
        Category("Card dues", 0xFFD6C48F) to listOf("credit card"),
    )
    private val OTHER = Category("Other", 0xFF8A90A6)

    fun categorize(merchant: String): Category {
        val m = merchant.lowercase()
        return CATEGORIES.firstOrNull { (_, hits) -> hits.any { it in m } }?.first ?: OTHER
    }

    fun parse(text: String, id: Int): ParseResult {
        val amountMatch = AMOUNT.find(text)
            ?: return ParseResult.Skipped(id, text, "No amount found")

        val isOut = OUT_VERB.containsMatchIn(text)
        val isIn = IN_VERB.containsMatchIn(text)
        if (!isOut && !isIn) return ParseResult.Skipped(id, text, "No debit or credit verb")

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            ?: return ParseResult.Skipped(id, text, "Unreadable amount")

        val direction = if (isIn && !isOut) Direction.IN else Direction.OUT

        val merchant = (
            VPA.find(text)?.groupValues?.get(1)
                ?: AT.find(text)?.groupValues?.get(1)
                ?: (if (direction == Direction.IN)
                        FROM.find(text)?.groupValues?.get(1) ?: BY.find(text)?.groupValues?.get(1)
                    else null)
                ?: TO.find(text)?.groupValues?.get(1)
                ?: TOWARDS.find(text)?.groupValues?.get(1)
                ?: "Unknown"
            ).trim().replace(TRAILING_PUNCT, "")

        val d = DATE.find(text)

        return ParseResult.Parsed(
            Txn(
                id = id,
                raw = text,
                direction = direction,
                amount = amount,
                merchant = merchant,
                account = ACCOUNT.find(text)?.groupValues?.get(1),
                date = d?.let { "${it.groupValues[1]} ${it.groupValues[2]}" },
                channel = CHANNEL.find(text)?.groupValues?.get(1)?.uppercase() ?: "BANK",
                bank = BANK.find(text)?.groupValues?.get(1)?.uppercase(),
                category = categorize(merchant.substringBefore('@')),
            )
        )
    }

    fun parseAll(messages: List<String>): List<ParseResult> =
        messages.mapIndexed { i, m -> parse(m, i) }
}
