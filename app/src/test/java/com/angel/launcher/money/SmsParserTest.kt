package com.angel.launcher.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * reference/sms-fixtures.tsv is the contract. Columns:
 * message, direction (OUT/IN/SKIP), amount, merchant, category-or-reason.
 */
class SmsParserTest {

    private val fixtures: List<List<String>> by lazy {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("sms-fixtures.tsv")) {
            "sms-fixtures.tsv missing from test resources"
        }
        stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
    }

    @Test
    fun `every fixture parses the way the reference says it does`() {
        assertTrue("no fixtures loaded", fixtures.isNotEmpty())

        fixtures.forEachIndexed { index, row ->
            val message = row[0]
            val expected = row[1]
            val result = SmsParser.parse(message, index)

            if (expected == "SKIP") {
                assertTrue("expected a skip for: $message", result is ParseResult.Skipped)
                assertEquals(message, row[4], (result as ParseResult.Skipped).why)
            } else {
                assertTrue("expected a parse for: $message", result is ParseResult.Parsed)
                val txn = (result as ParseResult.Parsed).txn
                assertEquals(
                    message,
                    if (expected == "OUT") Direction.OUT else Direction.IN,
                    txn.direction,
                )
                assertEquals(message, row[2].toDouble(), txn.amount, 0.001)
                assertEquals(message, row[3], txn.merchant)
                assertEquals(message, row[4], txn.category.key)
            }
        }
    }

    @Test
    fun `parseAll keeps the skipped bucket rather than dropping it`() {
        val messages = fixtures.map { it[0] }
        val results = SmsParser.parseAll(messages)
        assertEquals(messages.size, results.size)
        assertEquals(
            fixtures.count { it[1] == "SKIP" },
            results.count { it is ParseResult.Skipped },
        )
    }
}
