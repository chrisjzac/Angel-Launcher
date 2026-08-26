package com.angel.launcher.money

import com.angel.launcher.money.db.CategoryRuleEntity
import com.angel.launcher.money.db.TxnDirection
import com.angel.launcher.money.db.TxnMode

/**
 * On-device merchant → category mapping. userRules come from a long-press
 * recategorize (see WealthRepository.recategorize) and are always checked
 * first, so a user's own call permanently overrides the shipped defaults for
 * that merchant.
 */
object Categorizer {
    const val FOOD = "Food & Dining"
    const val GROCERIES = "Groceries"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health"
    const val TRANSFERS = "Transfers"
    const val CASH = "Cash"
    const val INCOME = "Income"
    const val OTHER = "Other"

    val ALL = listOf(
        FOOD, GROCERIES, TRANSPORT, SHOPPING, BILLS,
        ENTERTAINMENT, HEALTH, TRANSFERS, CASH, INCOME, OTHER,
    )

    private val INCOME_HINTS = listOf(
        "salary", "payout", "interest", "dividend", "refund", "cashback", "reimbursement",
    )

    /** Order matters — first hit wins. */
    private val BUILTIN: List<Pair<String, List<String>>> = listOf(
        FOOD to listOf(
            "swiggy", "zomato", "dominos", "pizza", "cafe", "restaurant", "starbucks",
            "eatsure", "dunzo", "tokai",
        ),
        GROCERIES to listOf(
            "bigbasket", "blinkit", "zepto", "grofers", "dmart", "grocery", "instamart", "jiomart",
        ),
        TRANSPORT to listOf(
            "uber", "ola", "rapido", "indianoil", "hpcl", "bharat petroleum", "iocl",
            "petrol", "diesel", "fuel", "metro", "irctc", "redbus", "fastag",
        ),
        SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho"),
        BILLS to listOf(
            "bescom", "airtel", "jio", "vodafone", "electricity", "electric board",
            "water board", "broadband", "fibernet", "gas", "dth", "tatasky",
            "credit card", "card bill", "cc bill",
        ),
        ENTERTAINMENT to listOf(
            "netflix", "spotify", "prime video", "hotstar", "bookmyshow", "youtube", "sonyliv", "zee5",
        ),
        HEALTH to listOf(
            "pharmeasy", "apollo", "netmeds", "hospital", "clinic", "medplus", "practo", "cult.fit", "healthkart",
        ),
    )

    fun categorize(
        merchantDisplay: String,
        direction: TxnDirection,
        mode: TxnMode,
        userRules: List<CategoryRuleEntity> = emptyList(),
    ): String {
        val m = merchantDisplay.lowercase()

        userRules.firstOrNull { it.matchPattern.lowercase() in m }?.let { return it.category }

        if (mode == TxnMode.ATM) return CASH
        if (direction == TxnDirection.CREDIT && INCOME_HINTS.any { it in m }) return INCOME

        BUILTIN.forEach { (category, hints) -> if (hints.any { it in m }) return category }

        if (mode == TxnMode.UPI || direction == TxnDirection.CREDIT) return TRANSFERS
        return OTHER
    }
}
