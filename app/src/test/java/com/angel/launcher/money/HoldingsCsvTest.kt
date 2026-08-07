package com.angel.launcher.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingsCsvTest {

    @Test
    fun `reads a broker export with cost basis`() {
        val csv = """
            Instrument,Qty.,Avg. cost,LTP,Cur. val
            INFY,40,1480.00,1596.40,63856.00
            TCS,12,3720.00,3588.15,43057.80
        """.trimIndent()

        val holdings = HoldingsCsv.parse(csv)

        assertEquals(2, holdings.size)
        assertEquals("INFY", holdings[0].symbol)
        assertEquals(40, holdings[0].quantity)
        assertEquals(1480.0, holdings[0].average, 0.001)
        assertEquals(1596.40, holdings[0].last, 0.001)
    }

    /** A depository statement lists what you hold, not what you paid. */
    @Test
    fun `a holding with no cost basis opens flat rather than inventing profit`() {
        val csv = """
            ISIN,Security Name,Current Bal,Market Price,Value
            INE009A01021,INFOSYS LIMITED,40,1596.40,63856.00
        """.trimIndent()

        val holdings = HoldingsCsv.parse(csv)

        assertEquals(1, holdings.size)
        assertEquals("INFOSYS LIMITED", holdings[0].name)
        assertEquals(40, holdings[0].quantity)
        assertEquals(0.0, holdings[0].profit, 0.001)
    }

    @Test
    fun `skips the preamble exports put above the header`() {
        val csv = """
            CDSL Holding Statement
            Generated on 07-Aug-2026

            Symbol,Quantity,Average Price,Price
            RELIANCE,18,2740.00,2891.05
        """.trimIndent()

        val holdings = HoldingsCsv.parse(csv)

        assertEquals(1, holdings.size)
        assertEquals("RELIANCE", holdings[0].symbol)
    }

    @Test
    fun `handles quoted fields, thousands separators and blank rows`() {
        val csv = """
            Symbol,Company,Qty,Avg Cost,LTP
            "NIFTYBEES","Nippon India ETF, Nifty 50",150,"248.00","271.60"

            ,,,,
        """.trimIndent()

        val holdings = HoldingsCsv.parse(csv)

        assertEquals(1, holdings.size)
        assertEquals("Nippon India ETF, Nifty 50", holdings[0].name)
        assertEquals(150, holdings[0].quantity)
        assertEquals(248.0, holdings[0].average, 0.001)
    }

    @Test
    fun `a file that is not a holdings export yields nothing`() {
        assertTrue(HoldingsCsv.parse("hello,world\n1,2").isEmpty())
        assertTrue(HoldingsCsv.parse("").isEmpty())
    }
}
