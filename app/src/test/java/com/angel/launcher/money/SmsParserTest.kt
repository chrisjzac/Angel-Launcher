package com.angel.launcher.money

import com.angel.launcher.money.db.AccountType
import com.angel.launcher.money.db.TxnDirection
import com.angel.launcher.money.db.TxnMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * One realistic sample per supported bank / card / UPI-app format, plus the
 * message shapes SmsParser must explicitly reject. This is the parser's
 * contract — a new bank format is a new Case here, not a change to parse().
 */
class SmsParserTest {

    private data class Case(
        val message: String,
        val direction: TxnDirection,
        val amountRupees: String,
        val issuer: String,
        val last4: String?,
        val accountType: AccountType,
        val mode: TxnMode,
        val merchant: String,
        val balanceRupees: String? = null,
    )

    private val cases = listOf(
        // HDFC Bank — UPI debit, credit card spend.
        Case(
            "HDFC Bank: Rs.450.00 debited from A/c XX1234 on 05-Aug-26 to VPA bluetokai@okhdfcbank. Ref 902133.",
            TxnDirection.DEBIT, "450.00", "HDFC Bank", "1234", AccountType.BANK, TxnMode.UPI, "Bluetokai",
        ),
        Case(
            "Alert: You've spent INR 1200.00 on your HDFC Bank Credit Card ending 3344 at DOMINOS on 05-Aug-26.",
            TxnDirection.DEBIT, "1200.00", "HDFC Bank", "3344", AccountType.CREDIT_CARD, TxnMode.CARD, "Dominos",
        ),
        // ICICI Bank — card spend, UPI credit with no captured counterparty.
        Case(
            "INR 899.00 spent on ICICI Bank Card XX9012 on 04-Aug-26 at AMAZON. Avl Lmt: INR 49650.00",
            TxnDirection.DEBIT, "899.00", "ICICI Bank", "9012", AccountType.CREDIT_CARD, TxnMode.CARD, "Amazon",
        ),
        Case(
            "Dear Customer, your ICICI Bank A/c XX7890 is credited with INR 5000.00 on 05-Aug-26. " +
                "UPI Ref 123456789012. Avl Bal: INR 25000.00",
            TxnDirection.CREDIT, "5000.00", "ICICI Bank", "7890", AccountType.BANK, TxnMode.UPI, "Unknown",
            "25000.00",
        ),
        // State Bank of India — UPI debit and a person-to-person credit.
        Case(
            "Dear UPI user, A/C X1234 debited by Rs 500.00 on 05Aug26 trf to CHAIPOINT. Refno 445512 -SBI",
            TxnDirection.DEBIT, "500.00", "State Bank of India", "1234", AccountType.BANK, TxnMode.UPI, "Chaipoint",
        ),
        Case(
            "Dear Customer, your A/c X1234 is credited with Rs 5000.00 on 05Aug26 from RAHUL SHARMA. " +
                "Ref 998877 -SBI",
            TxnDirection.CREDIT, "5000.00", "State Bank of India", "1234", AccountType.BANK, TxnMode.OTHER,
            "Rahul Sharma",
        ),
        // Axis Bank — structured "Info: UPI/.../payee" field, debit and credit, plus a credit card charge.
        Case(
            "Axis Bank: INR 250.00 debited from A/c no. XX5678 on 05-08-26. Info: UPI/P2M/123456789012/bluetokai. " +
                "Avl Bal: INR 12345.00",
            TxnDirection.DEBIT, "250.00", "Axis Bank", "5678", AccountType.BANK, TxnMode.UPI, "Bluetokai",
            "12345.00",
        ),
        Case(
            "Axis Bank: INR 1000.00 deposited to A/c no. XX5678 on 05-08-26. Info: UPI/P2A/123456789/John Doe. " +
                "Avl Bal: INR 13345.00",
            TxnDirection.CREDIT, "1000.00", "Axis Bank", "5678", AccountType.BANK, TxnMode.UPI, "John Doe",
            "13345.00",
        ),
        Case(
            "Axis Bank Credit Card XX4321 has been used for a payment of INR 999.00 at BIGBASKET on 05-08-26 15:23:11",
            TxnDirection.DEBIT, "999.00", "Axis Bank", "4321", AccountType.CREDIT_CARD, TxnMode.CARD, "Bigbasket",
        ),
        // Kotak Mahindra Bank — UPI sent/received, credit card.
        Case(
            "Sent Rs.500.00 from Kotak Bank AC X1234 to merchant@ybl on 05-08-26. UPI Ref:123456789012.",
            TxnDirection.DEBIT, "500.00", "Kotak Mahindra Bank", "1234", AccountType.BANK, TxnMode.UPI, "Merchant",
        ),
        Case(
            "Received Rs.1000.00 in your Kotak Bank AC X1234 from sender@okaxis on 05-08-26. UPI Ref 123456789012.",
            TxnDirection.CREDIT, "1000.00", "Kotak Mahindra Bank", "1234", AccountType.BANK, TxnMode.UPI, "Sender",
        ),
        Case(
            "Kotak Credit Card XX7788 used for a payment of Rs.799.00 at ZOMATO on 05-08-26.",
            TxnDirection.DEBIT, "799.00", "Kotak Mahindra Bank", "7788", AccountType.CREDIT_CARD, TxnMode.CARD,
            "Zomato",
        ),
        // IDFC FIRST Bank — bare "UPI/vpa" reference with no "Info:" wrapper.
        Case(
            "Rs.399.00 debited from A/c XX2233 on 05-Aug-26 towards UPI/merchant@idfcbank. Avl Bal Rs.5000.00 " +
                "-IDFC FIRST Bank",
            TxnDirection.DEBIT, "399.00", "IDFC FIRST Bank", "2233", AccountType.BANK, TxnMode.UPI, "Merchant",
            "5000.00",
        ),
        // Yes Bank — debit phrased as "debited ... & credited to", and a credit.
        Case(
            "Rs 500.00 debited from A/c XX6655 & credited to merchant@ybl UPI Ref 123456789012 on 05-Aug-26 -Yes Bank",
            TxnDirection.DEBIT, "500.00", "Yes Bank", "6655", AccountType.BANK, TxnMode.UPI, "Merchant",
        ),
        Case(
            "Your A/c XX6655 credited with Rs 2000.00 on 05-Aug-26 via UPI from RAVI KUMAR. Ref 123456 -Yes Bank",
            TxnDirection.CREDIT, "2000.00", "Yes Bank", "6655", AccountType.BANK, TxnMode.UPI, "Ravi Kumar",
        ),
        // IndusInd Bank.
        Case(
            "Rs.350.00 debited from A/c XX7711 to merchant@induc. UPI Ref 123456789012. -IndusInd Bank",
            TxnDirection.DEBIT, "350.00", "IndusInd Bank", "7711", AccountType.BANK, TxnMode.UPI, "Merchant",
        ),
        // RBL Bank — UPI debit and a credit card charge.
        Case(
            "RBL Bank: Rs 599.00 debited from A/c XX8822 to merchant@rbl. UPI Ref 123456789012. Avl Bal Rs 3000.00",
            TxnDirection.DEBIT, "599.00", "RBL Bank", "8822", AccountType.BANK, TxnMode.UPI, "Merchant",
            "3000.00",
        ),
        Case(
            "Your RBL Bank Credit Card XX9933 has been charged with INR 799.00 at STARBUCKS on 05-Aug-26.",
            TxnDirection.DEBIT, "799.00", "RBL Bank", "9933", AccountType.CREDIT_CARD, TxnMode.CARD, "Starbucks",
        ),
        // Federal Bank.
        Case(
            "Rs.450.00 debited from A/c XX1122 to merchant@fbl. UPI Ref 123456789012. Avl Bal Rs.2300.00 " +
                "-Federal Bank",
            TxnDirection.DEBIT, "450.00", "Federal Bank", "1122", AccountType.BANK, TxnMode.UPI, "Merchant",
            "2300.00",
        ),
        // American Express — always a credit card, "ending" last-4 rather than a masked one.
        Case(
            "INR 2499.00 spent using your American Express Card ending 1005 at MARRIOTT on 05 Aug 26.",
            TxnDirection.DEBIT, "2499.00", "American Express", "1005", AccountType.CREDIT_CARD, TxnMode.CARD,
            "Marriott",
        ),
        Case(
            "A payment of INR 5000.00 has been received towards your American Express Card ending 1005 " +
                "on 05 Aug 26. Thank you.",
            TxnDirection.CREDIT, "5000.00", "American Express", "1005", AccountType.CREDIT_CARD, TxnMode.CARD,
            "Unknown",
        ),
        // GPay / PhonePe / Paytm — no bank account at all, amount always sits between verb and preposition.
        Case(
            "You paid Rs.150.00 to Sharma General Store using Google Pay. UPI transaction ID 123456789012.",
            TxnDirection.DEBIT, "150.00", "Google Pay", null, AccountType.BANK, TxnMode.UPI, "Sharma General Store",
        ),
        Case(
            "Rs.500.00 received from Ramesh Kumar via PhonePe. UPI Ref 123456789012.",
            TxnDirection.CREDIT, "500.00", "PhonePe", null, AccountType.BANK, TxnMode.UPI, "Ramesh Kumar",
        ),
        Case(
            "Rs.250.00 paid to Corner Cafe successfully via Paytm UPI. Ref No 123456789012.",
            TxnDirection.DEBIT, "250.00", "Paytm", null, AccountType.BANK, TxnMode.UPI, "Corner Cafe",
        ),
    )

    private val rejectCases = listOf(
        "123456 is your OTP for txn of Rs.500 at MERCHANT. Do not share with anyone. -HDFC Bank" to
            "OTP message, not a transaction",
        "Your transaction of Rs.500 at MERCHANT has been declined due to insufficient balance. -HDFC Bank" to
            "Transaction failed or was declined",
        "Payment of Rs.999.00 via UPI failed. Please retry. -ICICI Bank" to
            "Transaction failed or was declined",
        "Get 40% off on your next 3 rides! Use code RIDE40. T&C apply." to
            "Promotional message",
        "Your EMI of Rs.2500.00 for Loan XX1234 will be due on 10-Aug-26. Pay on time to avoid late fee. " +
            "-HDFC Bank" to "Payment reminder, not a completed transaction",
        "Your A/c XX1234 available balance is Rs.15000.00 as on 05-Aug-26. -HDFC Bank" to
            "Balance enquiry, not a transaction",
        "Welcome to HDFC Bank NetBanking. Please update your KYC details before 30-Sep-26 to continue " +
            "using our services." to "No amount found",
    )

    @Test
    fun `every supported bank format parses correctly`() {
        cases.forEach { case ->
            val result = SmsParser.parse(case.message)
            assertTrue("expected a parse for: ${case.message}", result is ParseOutcome.Parsed)
            val txn = (result as ParseOutcome.Parsed).txn
            assertEquals(case.message, case.direction, txn.direction)
            assertEquals(case.message, rupeesToPaise(case.amountRupees), txn.amountPaise)
            assertEquals(case.message, case.issuer, txn.issuer)
            assertEquals(case.message, case.last4, txn.last4)
            assertEquals(case.message, case.accountType, txn.accountType)
            assertEquals(case.message, case.mode, txn.mode)
            assertEquals(case.message, case.merchant, txn.merchantDisplay)
            if (case.balanceRupees != null) {
                assertEquals(case.message, rupeesToPaise(case.balanceRupees), txn.balanceAfterPaise)
            } else {
                assertNull(case.message, txn.balanceAfterPaise)
            }
        }
    }

    @Test
    fun `non-transactional messages are rejected with the right reason`() {
        rejectCases.forEach { (message, reason) ->
            val result = SmsParser.parse(message)
            assertTrue("expected a rejection for: $message", result is ParseOutcome.Rejected)
            assertEquals(message, reason, (result as ParseOutcome.Rejected).reason)
        }
    }

    @Test
    fun `every fixture is accounted for, none silently dropped`() {
        val messages = cases.map { it.message } + rejectCases.map { it.first }
        val results = messages.map { SmsParser.parse(it) }
        assertEquals(cases.size, results.count { it is ParseOutcome.Parsed })
        assertEquals(rejectCases.size, results.count { it is ParseOutcome.Rejected })
    }

    private fun rupeesToPaise(rupees: String): Long = BigDecimal(rupees).movePointRight(2).toLong()
}
